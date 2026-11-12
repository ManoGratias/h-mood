package com.hmood.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * Estimation du tempo (BPM) et de l'énergie d'un extrait audio, entièrement en local :
 * enveloppe d'attaques (variation d'énergie par trames) puis autocorrélation sur 60–200 BPM,
 * pondérée autour de 120 BPM. Le résultat est ramené dans l'intervalle 70–170 BPM.
 * Aucun audio n'est conservé : seuls les deux nombres résultants le sont.
 */
public final class TempoAnalyzer {

    /** @param bpm tempo estimé ; @param energy énergie RMS moyenne (0..1) */
    public record Result(double bpm, double energy) {
    }

    private static final int FRAME = 1024;
    private static final int HOP = 512;

    private TempoAnalyzer() {
    }

    /** Décode un MP3 (extrait) et l'analyse. */
    public static Result analyzeMp3(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        float[] samples = new float[1 << 20];
        int count = 0;
        int sampleRate = 44100;
        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                sampleRate = buffer.getSampleFrequency();
                int channels = Math.max(1, buffer.getChannelCount());
                short[] pcm = buffer.getBuffer();
                int frames = buffer.getBufferLength() / channels;
                // mono + sous-échantillonnage ×2 (suffisant pour détecter les attaques)
                for (int i = 0; i + 1 < frames; i += 2) {
                    float v = 0;
                    for (int c = 0; c < channels; c++) v += pcm[i * channels + c];
                    if (count == samples.length) samples = Arrays.copyOf(samples, samples.length * 2);
                    samples[count++] = v / (channels * 32768f);
                }
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
        return analyze(Arrays.copyOf(samples, count), sampleRate / 2);
    }

    /** Analyse d'un signal mono normalisé (-1..1). */
    public static Result analyze(float[] x, int sampleRate) {
        int n = (x.length - FRAME) / HOP;
        if (n < 64) return new Result(0, 0);
        double[] logEnergy = new double[n];
        double rms = 0;
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int start = i * HOP;
            for (int j = 0; j < FRAME; j++) {
                double v = x[start + j];
                sum += v * v;
            }
            rms += sum / FRAME;
            logEnergy[i] = Math.log(1e-9 + sum);
        }
        double energy = Math.sqrt(rms / n);

        // Fonction d'attaque : hausse d'énergie, moins sa moyenne locale
        double[] onset = new double[n];
        for (int i = 1; i < n; i++) onset[i] = Math.max(0, logEnergy[i] - logEnergy[i - 1]);
        double[] flux = new double[n];
        int w = 8;
        for (int i = 0; i < n; i++) {
            double local = 0;
            int c = 0;
            for (int j = Math.max(0, i - w); j < Math.min(n, i + w + 1); j++) {
                local += onset[j];
                c++;
            }
            flux[i] = Math.max(0, onset[i] - local / c);
        }

        double fps = (double) sampleRate / HOP;
        int minLag = Math.max(1, (int) Math.floor(60 * fps / 200));
        int maxLag = Math.min(n - 2, (int) Math.ceil(60 * fps / 60));
        if (maxLag <= minLag + 1) return new Result(0, energy);
        double[] r = new double[maxLag + 2];
        for (int lag = minLag - 1; lag <= maxLag + 1; lag++) {
            if (lag <= 0) continue;
            double s = 0;
            for (int i = lag; i < n; i++) s += flux[i] * flux[i - lag];
            r[lag] = s / (n - lag);
        }
        int bestLag = -1;
        double best = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60 * fps / lag;
            double octave = Math.log(bpm / 120.0) / Math.log(2);
            double weight = Math.exp(-0.5 * Math.pow(octave / 0.9, 2));
            double score = r[lag] * weight;
            if (score > best) {
                best = score;
                bestLag = lag;
            }
        }
        if (bestLag <= 0 || best <= 0) return new Result(0, energy);
        double lag = bestLag;
        double a = r[bestLag - 1], b = r[bestLag], c = r[bestLag + 1];
        double denom = a - 2 * b + c;
        if (denom != 0) lag += 0.5 * (a - c) / denom;
        double bpm = 60 * fps / lag;
        while (bpm < 70) bpm *= 2;
        while (bpm > 170) bpm /= 2;
        return new Result(Math.round(bpm * 10) / 10.0, energy);
    }
}
