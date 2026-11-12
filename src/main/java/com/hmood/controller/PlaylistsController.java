package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Playlist;
import com.hmood.model.Track;
import com.hmood.recommendation.PlaylistCriteria;
import com.hmood.ui.Dialogs;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/** Playlists : liste, détail, réordonnancement et Studio playlists (génération). */
public final class PlaylistsController implements PageController {

    @FXML private ListView<Playlist> playlistList;
    @FXML private VBox detailPane;
    @FXML private VBox noSelection;
    @FXML private Label playlistName;
    @FXML private Label playlistInfo;
    @FXML private Button playButton;
    @FXML private TableView<Track> trackTable;
    @FXML private Button upButton;
    @FXML private Button downButton;
    @FXML private Button removeButton;

    private final AppContext ctx;
    private final ObservableList<Track> entries = FXCollections.observableArrayList();

    public PlaylistsController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        playlistList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Playlist p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label name = Ui.label(p.name(), "track-title");
                Label info = Ui.label(p.trackCount() + " titres · " + Durations.human(p.totalDurationMs())
                        + (p.mood() != null ? " · " + p.mood().label() : ""), "track-artist");
                setGraphic(new VBox(2, name, info));
                setText(null);
            }
        });
        playlistList.setPlaceholder(Ui.muted("Aucune playlist pour l'instant."));
        playlistList.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> show(p));

        TableColumn<Track, Number> index = new TableColumn<>("#");
        index.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || getTableRow() == null || getTableRow().getItem() == null ? null : String.valueOf(getIndex() + 1));
            }
        });
        index.setPrefWidth(46);
        index.setSortable(false);
        TableColumn<Track, String> title = new TableColumn<>("TITRE");
        title.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().title()));
        title.setPrefWidth(300);
        TableColumn<Track, String> artist = new TableColumn<>("ARTISTE");
        artist.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().displayArtist()));
        artist.setPrefWidth(200);
        TableColumn<Track, String> mood = new TableColumn<>("HUMEUR");
        mood.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().mood() == null ? "" : c.getValue().mood().label()));
        mood.setPrefWidth(110);
        TableColumn<Track, String> duration = new TableColumn<>("DURÉE");
        duration.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Durations.format(c.getValue().durationMs())));
        duration.setPrefWidth(80);
        for (TableColumn<Track, ?> c : List.of(title, artist, mood, duration)) {
            c.setSortable(false); // l'ordre est celui de la playlist
        }
        trackTable.getColumns().setAll(List.of(index, title, artist, mood, duration));
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        trackTable.setItems(entries);
        trackTable.setPlaceholder(Ui.muted("Playlist vide. Ajoute des morceaux depuis Ma musique."));
        trackTable.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    ctx.player().play(new ArrayList<>(entries), row.getIndex());
                }
            });
            return row;
        });
        reload(null);
    }

    @Override
    public void onShow(Object argument) {
        if (argument instanceof Long id) {
            reload(id);
        }
    }

    private void reload(Long selectId) {
        Playlist selected = playlistList.getSelectionModel().getSelectedItem();
        long target = selectId != null ? selectId : selected != null ? selected.id() : -1;
        List<Playlist> all = ctx.playlists().all();
        playlistList.getItems().setAll(all);
        all.stream().filter(p -> p.id() == target).findFirst()
                .ifPresentOrElse(p -> playlistList.getSelectionModel().select(p),
                        () -> {
                            if (!all.isEmpty()) playlistList.getSelectionModel().select(0);
                            else show(null);
                        });
    }

    private void show(Playlist p) {
        detailPane.setVisible(p != null);
        noSelection.setVisible(p == null);
        if (p == null) {
            entries.clear();
            return;
        }
        playlistName.setText(p.name());
        StringJoiner info = new StringJoiner(" · ");
        if (p.description() != null && !p.description().isBlank()) info.add(p.description());
        info.add(p.trackCount() + " titres");
        info.add(Durations.human(p.totalDurationMs()));
        playlistInfo.setText(info.toString());
        entries.setAll(ctx.playlists().tracks(p.id()));
        playButton.setDisable(entries.isEmpty());
    }

    private Playlist selected() {
        return playlistList.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onNew() {
        Dialogs.askText(ctx, "Nouvelle playlist", "Nom :", "").ifPresent(name -> {
            long id = ctx.playlists().create(name, null, null);
            ctx.bumpLibrary();
            reload(id);
        });
    }

    @FXML
    private void onPlay() {
        if (!entries.isEmpty()) {
            ctx.player().play(new ArrayList<>(entries), 0);
        }
    }

    @FXML
    private void onRename() {
        Playlist p = selected();
        if (p == null) return;
        Dialogs.askText(ctx, "Renommer la playlist", "Nouveau nom :", p.name()).ifPresent(name -> {
            ctx.playlists().rename(p.id(), name);
            reload(p.id());
        });
    }

    @FXML
    private void onDelete() {
        Playlist p = selected();
        if (p == null) return;
        if (Dialogs.confirm(ctx, "Supprimer « " + p.name() + " » ?", "Les morceaux restent dans ta bibliothèque.")) {
            ctx.playlists().delete(p.id());
            playlistList.getSelectionModel().clearSelection();
            ctx.bumpLibrary();
            reload(null);
        }
    }

    @FXML private void onMoveUp() { move(-1); }
    @FXML private void onMoveDown() { move(1); }

    private void move(int delta) {
        int i = trackTable.getSelectionModel().getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= entries.size()) return;
        Collections.swap(entries, i, j);
        persist();
        trackTable.getSelectionModel().clearAndSelect(j);
    }

    @FXML
    private void onRemove() {
        int i = trackTable.getSelectionModel().getSelectedIndex();
        if (i < 0) return;
        entries.remove(i);
        persist();
    }

    private void persist() {
        Playlist p = selected();
        if (p == null) return;
        ctx.playlists().replaceTracks(p.id(), new ArrayList<>(entries));
        ctx.bumpLibrary();
        // Met à jour les compteurs de la liste sans recharger le détail
        int index = playlistList.getSelectionModel().getSelectedIndex();
        ctx.playlists().find(p.id()).ifPresent(updated -> {
            playlistList.getItems().set(index, updated);
            playlistList.getSelectionModel().select(index);
        });
    }

    // ------------------------------------------------------------------ Studio playlists

    @FXML
    private void onStudio() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Studio playlists");
        dialog.setHeaderText("Génère une playlist à partir de ta bibliothèque");
        Dialogs.prepare(dialog, ctx);

        TextField name = new TextField("Ma sélection du " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM")));
        ComboBox<String> mood = new ComboBox<>();
        mood.getItems().add("Toutes les humeurs");
        for (Mood m : Mood.values()) mood.getItems().add(m.label());
        mood.getSelectionModel().select(0);
        TextField genre = new TextField();
        genre.setPromptText("ex. rock, jazz, lo-fi (facultatif)");
        ComboBox<String> decade = new ComboBox<>();
        decade.getItems().add("Toutes les décennies");
        for (int d = 1950; d <= 2020; d += 10) decade.getItems().add("Années " + d);
        decade.getSelectionModel().select(0);
        Spinner<Integer> minutes = new Spinner<>(10, 300, 45, 5);
        minutes.setEditable(true);
        mood.valueProperty().addListener((o, a, v) -> {
            int i = mood.getSelectionModel().getSelectedIndex();
            if (i > 0) minutes.getValueFactory().setValue(Mood.values()[i - 1].suggestedMinutes());
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.addRow(0, new Label("Nom"), name);
        grid.addRow(1, new Label("Humeur"), mood);
        grid.addRow(2, new Label("Genre"), genre);
        grid.addRow(3, new Label("Décennie"), decade);
        grid.addRow(4, new Label("Durée (min)"), minutes);
        grid.add(Ui.muted("Le genre et l'année proviennent des tags de tes fichiers. L'humeur s'appuie sur les humeurs "
                + "que tu as associées et sur le genre."), 0, 5, 2, 1);
        grid.setPrefWidth(460);
        dialog.getDialogPane().setContent(grid);
        ButtonType generate = new ButtonType("Générer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(generate, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != generate) {
            return;
        }
        int moodIndex = mood.getSelectionModel().getSelectedIndex();
        Mood selectedMood = moodIndex > 0 ? Mood.values()[moodIndex - 1] : null;
        int decadeIndex = decade.getSelectionModel().getSelectedIndex();
        Integer selectedDecade = decadeIndex > 0 ? 1950 + (decadeIndex - 1) * 10 : null;
        String genreText = genre.getText() == null || genre.getText().isBlank() ? null : genre.getText().trim();
        PlaylistCriteria criteria = new PlaylistCriteria(selectedMood, genreText, selectedDecade, minutes.getValue());

        List<Track> tracks = ctx.playlists().generate(criteria);
        if (tracks.isEmpty()) {
            ctx.notify("Aucun morceau ne correspond à ces critères. Astuce : associe des humeurs à tes morceaux dans Ma musique.");
            return;
        }
        String playlistName = name.getText() == null || name.getText().isBlank() ? "Studio playlists" : name.getText().trim();
        long id = ctx.playlists().createWithTracks(playlistName, describe(criteria), selectedMood, tracks);
        ctx.bumpLibrary();
        reload(id);
        ctx.notify("Playlist « " + playlistName + " » générée : " + tracks.size() + " titres");
    }

    private static String describe(PlaylistCriteria c) {
        StringJoiner sj = new StringJoiner(" · ", "Studio playlists · ", "");
        if (c.mood() != null) sj.add(c.mood().label());
        if (c.genre() != null) sj.add(c.genre());
        if (c.decade() != null) sj.add("années " + c.decade());
        sj.add(c.targetMinutes() + " min visées");
        return sj.toString();
    }
}
