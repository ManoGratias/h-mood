package com.hmood.player;

import com.hmood.model.Track;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lecteur audio (JavaFX Media) avec enchaînement de DJ :
 * <ul>
 *     <li>préchargement du titre suivant pendant la lecture (aucun blanc entre deux titres) ;</li>
 *     <li>fondu croisé à puissance constante : les deux titres jouent ensemble pendant la transition ;</li>
 *     <li>calage du tempo : pendant la transition, le titre entrant est légèrement accéléré ou ralenti
 *     (±6 % au plus) pour coller au BPM du titre sortant, puis revient progressivement à sa vitesse.</li>
 * </ul>
 * File d'attente, aléatoire, répétition, volume et spectre. À utiliser sur le thread JavaFX.
 */
public final class PlayerService {

    public static final int SPECTRUM_BANDS = 48;
    public static final int SPECTRUM_THRESHOLD = -60;
    /** Écart de vitesse maximal appliqué pour caler les tempos (±6 %). */
    static final double MAX_RATE_SHIFT = 0.06;
    /** Durée du retour progressif à la vitesse normale après une transition. */
    private static final double RATE_RECOVERY_SECONDS = 10;

    private final ObservableList<Track> queue = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<Track> current = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper playing = new ReadOnlyBooleanWrapper();
    private final ReadOnlyLongWrapper positionMs = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper durationMs = new ReadOnlyLongWrapper();
    private final DoubleProperty volume = new SimpleDoubleProperty(0.7);
    /** Gain global (0..1) piloté par le minuteur de sommeil. */
    private final DoubleProperty masterGain = new SimpleDoubleProperty(1.0);
    /** Gain du titre courant (fondu d'entrée / de sortie / transition). */
    private final DoubleProperty fadeGain = new SimpleDoubleProperty(1.0);
    /** Gain du titre sortant pendant une transition. */
    private final DoubleProperty outGain = new SimpleDoubleProperty(0);
    /** Durée du fondu enchaîné en secondes (0 = enchaînement sec). */
    private final DoubleProperty crossfadeSeconds = new SimpleDoubleProperty(0);
    private final BooleanProperty shuffle = new SimpleBooleanProperty();
    private final ObjectProperty<RepeatMode> repeat = new SimpleObjectProperty<>(RepeatMode.OFF);
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private MediaPlayer mediaPlayer;
    private int index = -1;
    private boolean startNotified;
    private boolean fadeInPending;
    private long maxPositionMs;
    private int generation;
    private Consumer<float[]> spectrumConsumer = m -> { };
    private RemoteResolver remoteResolver = t -> {
        throw new IllegalStateException("Aucun catalogue en ligne configuré");
    };
    private Function<Track, Double> bpmLookup = t -> null;

    // Préchargement et transition
    private MediaPlayer nextPlayer;
    private int nextIndex = -1;
    private boolean nextReady;
    private MediaPlayer outgoing;
    private Timeline transition;
    private Timeline rateRecovery;
    private boolean inTransition;

    {
        shuffle.addListener((o, a, b) -> disposeNext());
        crossfadeSeconds.addListener((o, a, b) -> {
            if (b.doubleValue() <= 0) disposeNext();
        });
    }

    /** Résout l'URL de lecture d'un morceau distant (appel bloquant, exécuté hors du thread JavaFX). */
    @FunctionalInterface
    public interface RemoteResolver {
        String resolve(Track track) throws Exception;
    }

    public void setRemoteResolver(RemoteResolver resolver) {
        this.remoteResolver = resolver;
    }

    /** Fournit le tempo connu d'un morceau (null si inconnu) pour le calage des transitions. */
    public void setBpmLookup(Function<Track, Double> lookup) {
        this.bpmLookup = lookup == null ? t -> null : lookup;
    }

    // ================================================================= commandes

    public void play(List<Track> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        queue.setAll(tracks);
        load(Math.max(0, Math.min(startIndex, tracks.size() - 1)), true);
    }

    public void playAt(int i) {
        if (i >= 0 && i < queue.size()) load(i, true);
    }

    public void enqueue(Track track) {
        queue.add(track);
        if (current.get() == null) load(queue.size() - 1, true);
    }

    public void togglePlay() {
        if (mediaPlayer == null) {
            if (!queue.isEmpty()) load(Math.max(index, 0), true);
            return;
        }
        if (inTransition) completeTransitionNow();
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    public void next() {
        advance(false);
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (mediaPlayer != null && positionMs.get() > 3000) {
            mediaPlayer.seek(Duration.ZERO);
            return;
        }
        int i = index - 1;
        if (i < 0) i = repeat.get() == RepeatMode.ALL ? queue.size() - 1 : 0;
        load(i, true);
    }

    public void seek(double fraction) {
        if (mediaPlayer != null && durationMs.get() > 0) {
            if (inTransition) completeTransitionNow();
            double f = Math.max(0, Math.min(1, fraction));
            mediaPlayer.seek(Duration.millis(f * durationMs.get()));
        }
    }

    public void stop() {
        generation++;
        cancelTransition();
        disposeNext();
        finishCurrent(false);
        disposePlayer();
        playing.set(false);
        positionMs.set(0);
    }

    /** Met en pause sans changer la file (lecteur Deezer ouvert, pause Pomodoro…). */
    public void pause() {
        if (inTransition) completeTransitionNow();
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
    }

    public void cycleRepeat() {
        repeat.set(repeat.get().next());
        disposeNext(); // le titre suivant peut changer
    }

    /** Remplace un morceau modifié (favori, humeur) sans interrompre la lecture. */
    public void refreshTrack(Track updated) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id() == updated.id()) queue.set(i, updated);
        }
        if (current.get() != null && current.get().id() == updated.id()) current.set(updated);
    }

    public void dispose() {
        stop();
    }

    // ================================================================= chargement

    private void load(int i, boolean autoplay) {
        cancelTransition();
        disposeNext();
        finishCurrent(false);
        disposePlayer();
        index = i;
        Track track = queue.get(i);
        current.set(track);
        positionMs.set(0);
        durationMs.set(track.durationMs());
        maxPositionMs = 0;
        startNotified = false;
        int gen = ++generation;
        resolve(track, gen, uri -> {
            MediaPlayer mp = create(track, uri);
            if (mp == null) return;
            attach(mp, track, true);
            if (autoplay) mp.play();
        });
    }

    /** Résout la source (fichier local ou URL distante) puis rappelle {@code onReady} sur le thread JavaFX. */
    private void resolve(Track track, int gen, Consumer<String> onReady) {
        if (track.isRemote()) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return remoteResolver.resolve(track);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((uri, error) -> Platform.runLater(() -> {
                if (gen != generation) return;
                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    if (track == current.get()) playing.set(false);
                    notifyError(track, cause.getMessage());
                } else {
                    onReady.accept(uri);
                }
            }));
            return;
        }
        File file = new File(track.filePath());
        if (!file.isFile()) {
            if (track == current.get()) playing.set(false);
            notifyError(track, "fichier introuvable (" + track.filePath() + ")");
            return;
        }
        onReady.accept(file.toURI().toString());
    }

    private MediaPlayer create(Track track, String source) {
        try {
            return new MediaPlayer(new Media(source));
        } catch (MediaException | IllegalArgumentException | UnsupportedOperationException e) {
            notifyError(track, e.getMessage());
            return null;
        }
    }

    /** Fait du lecteur {@code mp} le lecteur courant et branche tous ses événements. */
    private void attach(MediaPlayer mp, Track track, boolean fadeIn) {
        mediaPlayer = mp;
        fadeInPending = fadeIn && crossfadeSeconds.get() > 0;
        fadeGain.set(fadeInPending ? 0 : 1);
        mp.volumeProperty().unbind();
        mp.volumeProperty().bind(volume.multiply(masterGain).multiply(fadeGain));
        mp.setAudioSpectrumNumBands(SPECTRUM_BANDS);
        mp.setAudioSpectrumInterval(0.05);
        mp.setAudioSpectrumThreshold(SPECTRUM_THRESHOLD);
        mp.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
            if (mp == mediaPlayer) spectrumConsumer.accept(magnitudes);
        });
        mp.currentTimeProperty().addListener((o, a, t) -> {
            if (mp != mediaPlayer) return;
            long ms = (long) t.toMillis();
            positionMs.set(ms);
            maxPositionMs = Math.max(maxPositionMs, ms);
            updateFade(ms);
            maybePreload(ms);
            maybeStartTransition(ms);
        });
        Runnable readDuration = () -> {
            Duration d = mp.getMedia().getDuration();
            if (mp == mediaPlayer && d != null && !d.isUnknown() && !d.isIndefinite() && d.toMillis() > 0) {
                durationMs.set((long) d.toMillis());
            }
        };
        mp.setOnReady(readDuration);
        if (mp.getStatus() == MediaPlayer.Status.READY || mp.getStatus() == MediaPlayer.Status.PLAYING) readDuration.run();
        mp.statusProperty().addListener((o, a, status) -> {
            if (mp != mediaPlayer) return;
            boolean isPlaying = status == MediaPlayer.Status.PLAYING;
            playing.set(isPlaying);
            if (isPlaying && !startNotified) {
                startNotified = true;
                listeners.forEach(l -> l.onStarted(track));
            }
        });
        mp.setOnEndOfMedia(() -> {
            if (mp != mediaPlayer || inTransition) return;
            finishCurrent(true);
            advance(true);
        });
        mp.setOnError(() -> {
            if (mp != mediaPlayer) return;
            playing.set(false);
            notifyError(track, mp.getError() != null ? mp.getError().getMessage() : "erreur inconnue");
        });
    }

    // ================================================================= fondus, préchargement, transitions

    /** Fondu d'entrée court au démarrage manuel ; fondu de sortie seulement si aucun titre n'est prêt à enchaîner. */
    private void updateFade(long ms) {
        if (inTransition) return;
        double fade = crossfadeSeconds.get() * 1000;
        if (fade <= 0) {
            fadeGain.set(1);
            return;
        }
        double in = fadeInPending ? Math.min(1, ms / Math.min(fade, 1500.0)) : 1;
        if (in >= 1) fadeInPending = false;
        double out = 1;
        long dur = durationMs.get();
        boolean willCrossfade = nextPlayer != null && nextReady;
        if (!willCrossfade && dur > 0) out = Math.max(0, Math.min(1, (dur - ms) / fade));
        fadeGain.set(Math.min(in, out));
    }

    /** Index du titre qui suivra (ou -1). */
    private int upcomingIndex() {
        if (queue.isEmpty()) return -1;
        if (repeat.get() == RepeatMode.ONE) return index;
        if (shuffle.get() && queue.size() > 1) {
            int i;
            do {
                i = random.nextInt(queue.size());
            } while (i == index);
            return i;
        }
        int i = index + 1;
        if (i >= queue.size()) return repeat.get() == RepeatMode.ALL ? 0 : -1;
        return i;
    }

    /** Précharge le titre suivant dès que le titre courant est lancé (fondu activé uniquement). */
    private void maybePreload(long ms) {
        if (crossfadeSeconds.get() <= 0 || nextPlayer != null || nextIndex >= 0 || inTransition || ms < 1500) return;
        int ni = upcomingIndex();
        if (ni < 0) return;
        nextIndex = ni;
        Track nt = queue.get(ni);
        int gen = generation;
        resolve(nt, gen, uri -> {
            if (gen != generation || nextIndex != ni) return;
            MediaPlayer np = create(nt, uri);
            if (np == null) {
                nextIndex = -1;
                return;
            }
            np.setVolume(0);
            nextPlayer = np;
            np.setOnReady(() -> {
                if (np == nextPlayer) nextReady = true;
            });
            if (np.getStatus() == MediaPlayer.Status.READY) nextReady = true;
        });
    }

    private void maybeStartTransition(long ms) {
        if (inTransition || nextPlayer == null || !nextReady) return;
        long dur = durationMs.get();
        double fade = crossfadeSeconds.get() * 1000;
        if (dur <= 0 || fade <= 0) return;
        if (dur - ms <= fade) startTransition(Math.max(500, Math.min(fade, dur - ms)));
    }

    /** Fondu croisé : le titre suivant démarre pendant que le courant se termine. */
    private void startTransition(double fadeMs) {
        inTransition = true;
        MediaPlayer out = mediaPlayer;
        Track outTrack = current.get();
        MediaPlayer in = nextPlayer;
        int inIndex = nextIndex;
        Track inTrack = queue.get(inIndex);
        nextPlayer = null;
        nextReady = false;
        nextIndex = -1;

        // Le titre sortant passe sur son propre gain
        outGain.set(fadeGain.get());
        out.volumeProperty().unbind();
        out.volumeProperty().bind(volume.multiply(masterGain).multiply(outGain));
        outgoing = out;
        finishCurrent(true);

        // Calage du tempo du titre entrant sur le sortant
        double rate = matchRate(bpmLookup.apply(outTrack), bpmLookup.apply(inTrack));
        in.setRate(rate);

        // Le titre entrant devient le titre courant (affichage, progression, historique)
        index = inIndex;
        current.set(inTrack);
        positionMs.set(0);
        durationMs.set(inTrack.durationMs());
        maxPositionMs = 0;
        attach(in, inTrack, false);
        fadeGain.set(0);
        in.play();
        startNotified = true;
        playing.set(true);
        listeners.forEach(l -> l.onStarted(inTrack));

        // Courbe à puissance constante : sin / cos
        DoubleProperty t = new SimpleDoubleProperty(0);
        t.addListener((o, a, v) -> {
            double x = v.doubleValue();
            fadeGain.set(Math.sin(x * Math.PI / 2));
            outGain.set(outGain.get() <= 0 ? 0 : Math.cos(x * Math.PI / 2));
        });
        transition = new Timeline(new KeyFrame(Duration.millis(fadeMs), new KeyValue(t, 1, Interpolator.LINEAR)));
        transition.setOnFinished(e -> {
            disposeOutgoing();
            inTransition = false;
            fadeGain.set(1);
            recoverRate(in);
        });
        transition.play();
    }

    /** Ratio de vitesse pour caler bpmIn sur bpmOut (en tenant compte des tempos doublés / moitiés). */
    static double matchRate(Double bpmOut, Double bpmIn) {
        if (bpmOut == null || bpmIn == null || bpmOut <= 0 || bpmIn <= 0) return 1.0;
        double best = 1.0;
        double bestDistance = Double.MAX_VALUE;
        for (double r : new double[]{bpmOut / bpmIn, 2 * bpmOut / bpmIn, bpmOut / (2 * bpmIn)}) {
            double d = Math.abs(r - 1);
            if (d < bestDistance) {
                bestDistance = d;
                best = r;
            }
        }
        return bestDistance <= MAX_RATE_SHIFT ? best : 1.0;
    }

    /** Retour progressif à la vitesse d'origine après la transition (imperceptible). */
    private void recoverRate(MediaPlayer mp) {
        if (rateRecovery != null) rateRecovery.stop();
        if (Math.abs(mp.getRate() - 1.0) < 0.001) return;
        rateRecovery = new Timeline(new KeyFrame(Duration.seconds(RATE_RECOVERY_SECONDS),
                new KeyValue(mp.rateProperty(), 1.0, Interpolator.EASE_BOTH)));
        rateRecovery.play();
    }

    private void completeTransitionNow() {
        if (!inTransition) return;
        if (transition != null) transition.stop();
        disposeOutgoing();
        inTransition = false;
        fadeGain.set(1);
        if (mediaPlayer != null) recoverRate(mediaPlayer);
    }

    private void cancelTransition() {
        if (transition != null) transition.stop();
        if (rateRecovery != null) rateRecovery.stop();
        disposeOutgoing();
        inTransition = false;
    }

    private void disposeOutgoing() {
        if (outgoing != null) {
            MediaPlayer o = outgoing;
            outgoing = null;
            o.volumeProperty().unbind();
            o.setAudioSpectrumListener(null);
            o.dispose();
        }
    }

    private void disposeNext() {
        if (nextPlayer != null) {
            MediaPlayer n = nextPlayer;
            nextPlayer = null;
            n.dispose();
        }
        nextReady = false;
        nextIndex = -1;
    }

    // ================================================================= enchaînement sans fondu

    private void advance(boolean automatic) {
        if (queue.isEmpty()) return;
        if (automatic && repeat.get() == RepeatMode.ONE) {
            load(index, true);
            return;
        }
        int nextIdx;
        if (shuffle.get() && queue.size() > 1) {
            do {
                nextIdx = random.nextInt(queue.size());
            } while (nextIdx == index);
        } else {
            nextIdx = index + 1;
            if (nextIdx >= queue.size()) {
                if (automatic && repeat.get() == RepeatMode.OFF) {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    return;
                }
                nextIdx = 0;
            }
        }
        load(nextIdx, true);
    }

    private void finishCurrent(boolean completed) {
        Track t = current.get();
        if (t != null && startNotified) {
            long listened = completed ? Math.max(maxPositionMs, durationMs.get()) : maxPositionMs;
            listeners.forEach(l -> l.onFinished(t, listened, completed));
        }
        startNotified = false;
    }

    private void disposePlayer() {
        if (mediaPlayer != null) {
            MediaPlayer mp = mediaPlayer;
            mediaPlayer = null;
            mp.setAudioSpectrumListener(null);
            mp.volumeProperty().unbind();
            mp.dispose();
        }
    }

    private void notifyError(Track track, String message) {
        listeners.forEach(l -> l.onError(track, message));
    }

    // ================================================================= accès

    public void addListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    public void setSpectrumConsumer(Consumer<float[]> consumer) {
        this.spectrumConsumer = consumer == null ? m -> { } : consumer;
    }

    public ObservableList<Track> getQueue() {
        return FXCollections.unmodifiableObservableList(queue);
    }

    public int currentIndex() {
        return index;
    }

    public ReadOnlyObjectProperty<Track> currentProperty() {
        return current.getReadOnlyProperty();
    }

    public Track getCurrent() {
        return current.get();
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing.getReadOnlyProperty();
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public ReadOnlyLongProperty positionMsProperty() {
        return positionMs.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty durationMsProperty() {
        return durationMs.getReadOnlyProperty();
    }

    public DoubleProperty volumeProperty() {
        return volume;
    }

    public DoubleProperty masterGainProperty() {
        return masterGain;
    }

    public DoubleProperty crossfadeSecondsProperty() {
        return crossfadeSeconds;
    }

    public BooleanProperty shuffleProperty() {
        return shuffle;
    }

    public ObjectProperty<RepeatMode> repeatProperty() {
        return repeat;
    }
}
