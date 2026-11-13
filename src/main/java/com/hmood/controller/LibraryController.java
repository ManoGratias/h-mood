package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Playlist;
import com.hmood.model.Track;
import com.hmood.service.LibraryService;
import com.hmood.service.LibraryService.ImportResult;
import com.hmood.ui.Covers;
import com.hmood.ui.Dialogs;
import com.hmood.ui.Icons;
import com.hmood.util.AudioFormats;
import com.hmood.util.Durations;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Ma musique : import, recherche, organisation de la bibliothèque locale. */
public final class LibraryController implements PageController {

    private static final String FILTER_ALL = "Tous les morceaux";
    private static final String FILTER_FAVORITES = "Favoris";
    private static final String FILTER_NO_MOOD = "Sans humeur";
    private static final String FILTER_MOOD_PREFIX = "Humeur : ";
    private static final PseudoClass PLAYING = PseudoClass.getPseudoClass("playing");

    @FXML private Label summaryLabel;
    @FXML private Button importFilesButton;
    @FXML private Button importFolderButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterBox;
    @FXML private HBox progressBox;
    @FXML private ProgressBar importProgress;
    @FXML private Label importLabel;
    @FXML private TableView<Track> table;
    @FXML private VBox emptyState;
    @FXML private Label formatsLabel;
    @FXML private ToggleButton tracksViewButton;
    @FXML private ToggleButton foldersViewButton;
    @FXML private HBox folderBar;
    @FXML private Label folderTitle;
    @FXML private Label folderPath;
    @FXML private ScrollPane foldersPane;
    @FXML private FlowPane foldersFlow;

    /** Vue par dossier active ; {@code openFolder} = dossier ouvert (null = grille des dossiers). */
    private boolean folderView;
    private String openFolder;

    private final AppContext ctx;
    private final ObservableList<Track> master = FXCollections.observableArrayList();
    private FilteredList<Track> filtered;
    private final ChangeListener<Track> currentTrackListener = (o, a, b) -> {
        if (table != null) {
            table.refresh();
        }
    };

    private final ChangeListener<Number> libraryListener = (o, a, b) -> reload();

    public LibraryController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        filtered = new FilteredList<>(master);
        SortedList<Track> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("Aucun morceau ne correspond à ce filtre."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        filterBox.getItems().addAll(FILTER_ALL, FILTER_FAVORITES, FILTER_NO_MOOD);
        for (Mood m : Mood.values()) {
            filterBox.getItems().add(FILTER_MOOD_PREFIX + m.label());
        }
        filterBox.getSelectionModel().select(FILTER_ALL);
        filterBox.valueProperty().addListener((o, a, b) -> applyFilter());
        searchField.textProperty().addListener((o, a, b) -> applyFilter());
        formatsLabel.setText("Formats pris en charge : " + AudioFormats.DESCRIPTION);

        ToggleGroup views = new ToggleGroup();
        tracksViewButton.setToggleGroup(views);
        foldersViewButton.setToggleGroup(views);
        folderView = "folders".equals(ctx.settings().libraryView());
        (folderView ? foldersViewButton : tracksViewButton).setSelected(true);
        views.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true); // toujours une vue sélectionnée
                return;
            }
            boolean folders = b == foldersViewButton;
            if (folders != folderView) setFolderView(folders, null);
        });

        buildColumns();
        buildRows();
        setupDragAndDrop();
        ctx.player().currentProperty().addListener(new WeakChangeListener<>(currentTrackListener));
        ctx.libraryRevisionProperty().addListener(new WeakChangeListener<>(libraryListener)); // ex. titre corrigé depuis les paroles
        reload();
    }

    @Override
    public void onShow(Object argument) {
        if (argument instanceof String query) {
            searchField.setText(query);
        }
    }

    private void reload() {
        try {
            master.setAll(ctx.library().all());
        } catch (RuntimeException e) {
            ctx.notify(e.getMessage());
        }
        if (openFolder != null && master.stream().noneMatch(t -> openFolder.equals(folderOf(t)))) {
            openFolder = null; // dossier vidé ou retiré
        }
        refreshView();
        long tagged = master.stream().filter(t -> t.mood() != null).count();
        long totalMs = master.stream().mapToLong(Track::durationMs).sum();
        summaryLabel.setText(master.size() + " morceaux · " + Durations.human(totalMs) + " · "
                + tagged + " associés à une humeur · double-clic pour lire, clic droit pour organiser");
    }

    private void applyFilter() {
        String query = searchField.getText();
        String filter = filterBox.getValue() == null ? FILTER_ALL : filterBox.getValue();
        String folder = folderView ? openFolder : null;
        filtered.setPredicate(t -> LibraryService.matches(t, query) && matchesFilter(t, filter)
                && (folder == null || folder.equals(folderOf(t))));
        if (folderView && openFolder == null) {
            buildFolders();
        }
    }

    // ------------------------------------------------------------------ vue par dossier

    /** Dossier qui contient le fichier du morceau (null pour un titre sans fichier local). */
    static String folderOf(Track t) {
        if (t.filePath() == null || t.filePath().isBlank() || t.isRemote()) return null;
        try {
            Path parent = Path.of(t.filePath()).getParent();
            return parent == null ? null : parent.toString();
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    static String folderName(String folder) {
        Path path = Path.of(folder);
        Path name = path.getFileName();
        if (name == null) return folder;
        Path parent = path.getParent();
        if (name.toString().equals("hors-ligne") && parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equalsIgnoreCase("HMood")) {
            return "Titres hors ligne"; // dossier des téléchargements Jamendo de l'appli
        }
        return name.toString();
    }

    private void setFolderView(boolean folders, String open) {
        folderView = folders;
        openFolder = folders ? open : null;
        ctx.settings().setLibraryView(folders ? "folders" : "tracks");
        if (folders != foldersViewButton.isSelected()) {
            (folders ? foldersViewButton : tracksViewButton).setSelected(true);
        }
        refreshView();
    }

    /** Affiche la liste, la grille des dossiers ou le contenu d'un dossier selon l'état courant. */
    private void refreshView() {
        boolean empty = master.isEmpty();
        boolean grid = folderView && openFolder == null;
        emptyState.setVisible(empty);
        table.setVisible(!empty && !grid);
        foldersPane.setVisible(!empty && grid);
        boolean inFolder = !empty && folderView && openFolder != null;
        folderBar.setVisible(inFolder);
        folderBar.setManaged(inFolder);
        if (inFolder) {
            List<Track> tracks = folderTracks(openFolder);
            long ms = tracks.stream().mapToLong(Track::durationMs).sum();
            folderTitle.setText(folderName(openFolder));
            folderPath.setText(tracks.size() + " morceau" + (tracks.size() > 1 ? "x" : "") + " · "
                    + Durations.human(ms) + " · " + openFolder);
        }
        applyFilter();
    }

    /** Grille des dossiers (ceux qui contiennent au moins un morceau correspondant à la recherche et au filtre). */
    private void buildFolders() {
        String query = searchField.getText();
        String needle = query == null ? "" : query.trim().toLowerCase();
        String filter = filterBox.getValue() == null ? FILTER_ALL : filterBox.getValue();
        Map<String, List<Track>> byFolder = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : master) {
            String folder = folderOf(t);
            if (folder == null || !matchesFilter(t, filter)) continue;
            boolean match = LibraryService.matches(t, query)
                    || (!needle.isEmpty() && folderName(folder).toLowerCase().contains(needle));
            if (match) byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(t);
        }
        foldersFlow.getChildren().clear();
        if (byFolder.isEmpty()) {
            Label none = new Label("Aucun dossier ne correspond à ce filtre.");
            none.getStyleClass().add("muted");
            foldersFlow.getChildren().add(none);
            return;
        }
        byFolder.forEach((folder, tracks) -> foldersFlow.getChildren().add(folderCard(folder, tracks)));
    }

    private VBox folderCard(String folder, List<Track> tracks) {
        HBox iconBox = new HBox(Icons.icon(Icons.FOLDER, 40));
        iconBox.getStyleClass().add("folder-icon");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button play = new Button();
        play.setGraphic(Icons.icon(Icons.PLAY, 18));
        play.getStyleClass().addAll("card-play", "folder-play");
        play.setTooltip(new Tooltip("Lire le dossier"));
        play.setOnAction(e -> playFolder(folder, false));
        HBox top = new HBox(iconBox, spacer, play);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(folderName(folder));
        name.getStyleClass().add("folder-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        long ms = tracks.stream().mapToLong(Track::durationMs).sum();
        Label meta = new Label(tracks.size() + " morceau" + (tracks.size() > 1 ? "x" : "") + " · " + Durations.human(ms));
        meta.getStyleClass().add("folder-meta");
        Label path = new Label(folder);
        path.getStyleClass().add("folder-path");
        path.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        path.setTooltip(new Tooltip(folder));

        VBox card = new VBox(8, top, name, meta, path);
        card.getStyleClass().addAll("track-card", "folder-card");
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) setFolderView(true, folder);
        });
        return card;
    }

    private List<Track> folderTracks(String folder) {
        return master.stream().filter(t -> folder.equals(folderOf(t)))
                .sorted((a, b) -> a.filePath().compareToIgnoreCase(b.filePath()))
                .toList();
    }

    private void playFolder(String folder, boolean shuffle) {
        List<Track> tracks = new ArrayList<>(folderTracks(folder));
        if (tracks.isEmpty()) return;
        if (shuffle) Collections.shuffle(tracks);
        ctx.player().play(tracks, 0);
    }

    @FXML
    private void onFolderBack() {
        setFolderView(true, null);
    }

    @FXML
    private void onFolderPlay() {
        if (openFolder == null) return;
        List<Track> visible = new ArrayList<>(table.getItems());
        if (!visible.isEmpty()) ctx.player().play(visible, 0);
    }

    @FXML
    private void onFolderShuffle() {
        if (openFolder != null) playFolder(openFolder, true);
    }

    private static boolean matchesFilter(Track t, String filter) {
        if (filter.equals(FILTER_FAVORITES)) return t.favorite();
        if (filter.equals(FILTER_NO_MOOD)) return t.mood() == null;
        if (filter.startsWith(FILTER_MOOD_PREFIX)) {
            return t.mood() != null && filter.equals(FILTER_MOOD_PREFIX + t.mood().label());
        }
        return true;
    }

    // ------------------------------------------------------------------ tableau

    private void buildColumns() {
        TableColumn<Track, Boolean> fav = new TableColumn<>("");
        fav.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().favorite()));
        fav.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(Icons.icon(value ? Icons.HEART : Icons.HEART_OUTLINE, 16));
                getStyleClass().removeAll("fav-cell", "fav-cell-on");
                getStyleClass().add(value ? "fav-cell-on" : "fav-cell");
                setOnMouseClicked(e -> {
                    Track t = getTableRow() == null ? null : getTableRow().getItem();
                    if (t != null && e.getClickCount() == 1) {
                        updateTracks(List.of(t), ctx.library()::toggleFavorite);
                        e.consume();
                    }
                });
            }
        });
        fav.setPrefWidth(44);
        fav.setMinWidth(44);
        fav.setMaxWidth(44);
        fav.setSortable(false);

        TableColumn<Track, Track> title = new TableColumn<>("TITRE");
        title.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        title.setComparator((a, b) -> a.title().compareToIgnoreCase(b.title()));
        title.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Track t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label label = new Label(t.title());
                label.getStyleClass().add("cell-title");
                label.setTextOverrun(OverrunStyle.ELLIPSIS);
                label.setMinWidth(0);
                HBox box = new HBox(10, Covers.of(t, 30), label);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });
        title.setPrefWidth(280);

        TableColumn<Track, String> artist = textColumn("ARTISTE", Track::displayArtist, 170);
        TableColumn<Track, String> album = textColumn("ALBUM", t -> t.album() == null ? "" : t.album(), 160);
        TableColumn<Track, String> genre = textColumn("GENRE", t -> t.genre() == null ? "" : t.genre(), 110);

        TableColumn<Track, Integer> year = new TableColumn<>("ANNÉE");
        year.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().year()));
        year.setPrefWidth(70);

        TableColumn<Track, Mood> mood = new TableColumn<>("HUMEUR");
        mood.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().mood()));
        mood.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Mood m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label pill = new Label(m.label());
                pill.getStyleClass().add("mood-pill");
                pill.setStyle("-fx-background-color: " + com.hmood.ui.Ui.moodGradient(m) + ";");
                setGraphic(pill);
                setText(null);
            }
        });
        mood.setPrefWidth(110);

        TableColumn<Track, Long> duration = new TableColumn<>("DURÉE");
        duration.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().durationMs()));
        duration.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long ms, boolean empty) {
                super.updateItem(ms, empty);
                setText(empty || ms == null ? null : Durations.format(ms));
            }
        });
        duration.setPrefWidth(70);

        TableColumn<Track, Integer> plays = new TableColumn<>("ÉCOUTES");
        plays.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().playCount()));
        plays.setPrefWidth(76);

        table.getColumns().setAll(List.of(fav, title, artist, album, genre, year, mood, duration, plays));
    }

    private static TableColumn<Track, String> textColumn(String name, Function<Track, String> getter, double width) {
        TableColumn<Track, String> col = new TableColumn<>(name);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private void buildRows() {
        table.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>() {
                @Override
                protected void updateItem(Track t, boolean empty) {
                    super.updateItem(t, empty);
                    Track current = ctx.player().getCurrent();
                    pseudoClassStateChanged(PLAYING, !empty && t != null && current != null && current.id() == t.id());
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    playFrom(row.getItem());
                }
            });
            row.setOnContextMenuRequested(e -> {
                if (!row.isEmpty()) {
                    if (!table.getSelectionModel().getSelectedItems().contains(row.getItem())) {
                        table.getSelectionModel().clearAndSelect(row.getIndex());
                    }
                    contextMenu(row.getItem()).show(row, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });
            return row;
        });
    }

    private void playFrom(Track track) {
        List<Track> list = new ArrayList<>(table.getItems());
        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == track.id()) {
                index = i;
                break;
            }
        }
        ctx.player().play(list, index);
    }

    private List<Track> selectionOr(Track t) {
        List<Track> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        return selected.contains(t) ? selected : List.of(t);
    }

    private ContextMenu contextMenu(Track track) {
        List<Track> targets = selectionOr(track);
        String countSuffix = targets.size() > 1 ? " (" + targets.size() + ")" : "";

        MenuItem play = new MenuItem("Lire");
        play.setOnAction(e -> playFrom(track));

        MenuItem enqueue = new MenuItem("Ajouter à la file d'attente" + countSuffix);
        enqueue.setOnAction(e -> {
            targets.forEach(ctx.player()::enqueue);
            ctx.notify(targets.size() + " morceau(x) ajouté(s) à la file d'attente");
        });

        Menu toPlaylist = new Menu("Ajouter à la playlist" + countSuffix);
        for (Playlist p : ctx.playlists().all()) {
            MenuItem item = new MenuItem(p.name());
            item.setOnAction(e -> addToPlaylist(p.id(), p.name(), targets));
            toPlaylist.getItems().add(item);
        }
        if (!toPlaylist.getItems().isEmpty()) {
            toPlaylist.getItems().add(new SeparatorMenuItem());
        }
        MenuItem newPlaylist = new MenuItem("Nouvelle playlist…");
        newPlaylist.setOnAction(e -> Dialogs.askText(ctx, "Nouvelle playlist", "Nom :", "").ifPresent(name -> {
            long id = ctx.playlists().create(name, null, null);
            addToPlaylist(id, name, targets);
        }));
        toPlaylist.getItems().add(newPlaylist);

        Menu moodMenu = new Menu("Humeur" + countSuffix);
        for (Mood m : Mood.values()) {
            MenuItem item = new MenuItem((track.mood() == m ? "✓  " : "     ") + m.label());
            item.setOnAction(e -> updateTracks(targets, t -> ctx.library().setMood(t, m)));
            moodMenu.getItems().add(item);
        }
        moodMenu.getItems().add(new SeparatorMenuItem());
        MenuItem noMood = new MenuItem("Aucune humeur");
        noMood.setOnAction(e -> updateTracks(targets, t -> ctx.library().setMood(t, null)));
        moodMenu.getItems().add(noMood);

        MenuItem editInfo = new MenuItem("Modifier le titre et l'artiste…");
        editInfo.setOnAction(e -> Dialogs.askTrackInfo(ctx, track.title(),
                        com.hmood.music.service.LyricsService.hasArtist(track) ? track.artist() : "")
                .ifPresent(v -> updateTracks(List.of(track), t -> ctx.library().setInfo(t, v[0], v[1]))));

        MenuItem favorite = new MenuItem(track.favorite() ? "Retirer des favoris" : "Ajouter aux favoris");
        boolean makeFavorite = !track.favorite();
        favorite.setOnAction(e -> updateTracks(targets,
                t -> t.favorite() == makeFavorite ? t : ctx.library().toggleFavorite(t)));

        MenuItem remove = new MenuItem("Retirer de la bibliothèque" + countSuffix);
        remove.getStyleClass().add("danger-item");
        remove.setOnAction(e -> {
            if (Dialogs.confirm(ctx, "Retirer " + targets.size() + " morceau(x) de la bibliothèque ?",
                    "Les fichiers ne sont pas supprimés du disque. Ils disparaissent aussi des playlists.")) {
                targets.forEach(ctx.library()::remove);
                reload();
                ctx.bumpLibrary();
            }
        });

        return new ContextMenu(play, enqueue, toPlaylist, moodMenu, favorite, editInfo, new SeparatorMenuItem(), remove);
    }

    private void addToPlaylist(long playlistId, String name, List<Track> tracks) {
        try {
            ctx.playlists().addTracks(playlistId, tracks);
            ctx.bumpLibrary();
            ctx.notify(tracks.size() + " morceau(x) ajouté(s) à « " + name + " »");
        } catch (RuntimeException ex) {
            ctx.notify(ex.getMessage());
        }
    }

    private void updateTracks(List<Track> tracks, Function<Track, Track> change) {
        try {
            for (Track t : tracks) {
                Track updated = change.apply(t);
                ctx.player().refreshTrack(updated);
            }
            reload();
            ctx.bumpLibrary();
        } catch (RuntimeException e) {
            ctx.notify(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ import

    @FXML
    private void onImportFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer des fichiers audio");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio (" + AudioFormats.DESCRIPTION + ")",
                AudioFormats.SUPPORTED.stream().map(ext -> "*." + ext).toList()));
        List<File> files = chooser.showOpenMultipleDialog(window());
        if (files != null && !files.isEmpty()) {
            runImport(files.stream().map(File::toPath).toList());
        }
    }

    @FXML
    private void onImportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Importer un dossier de musique");
        File dir = chooser.showDialog(window());
        if (dir != null) {
            runImport(List.of(dir.toPath()), dir.toPath());
        }
    }

    private void runImport(List<Path> paths) {
        runImport(paths, null);
    }

    /** {@code folder} : dossier importé, ouvert à la fin dans la vue par dossier. */
    private void runImport(List<Path> paths, Path folder) {
        Task<ImportResult> task = new Task<>() {
            @Override
            protected ImportResult call() {
                updateMessage("Analyse des fichiers…");
                return ctx.library().importPaths(paths, (done, total) -> {
                    updateProgress(done, total);
                    updateMessage(done + " / " + total + " fichiers");
                });
            }
        };
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        importFilesButton.setDisable(true);
        importFolderButton.setDisable(true);
        importProgress.progressProperty().bind(task.progressProperty());
        importLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            endImport();
            reload();
            ctx.bumpLibrary();
            ctx.notify(task.getValue().summary());
            if (folder != null) showImportedFolder(folder);
        });
        task.setOnFailed(e -> {
            endImport();
            ctx.notify("Import interrompu : " + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "hmood-import");
        thread.setDaemon(true);
        thread.start();
    }

    /** Après l'import d'un dossier : ouvre ce dossier s'il contient directement des morceaux, sinon la grille. */
    private void showImportedFolder(Path folder) {
        String key = folder.toString();
        String open = master.stream().map(LibraryController::folderOf)
                .filter(f -> f != null && f.equalsIgnoreCase(key)).findFirst().orElse(null);
        setFolderView(true, open);
    }

    private void endImport() {
        importProgress.progressProperty().unbind();
        importLabel.textProperty().unbind();
        progressBox.setVisible(false);
        progressBox.setManaged(false);
        importFilesButton.setDisable(false);
        importFolderButton.setDisable(false);
    }

    private void setupDragAndDrop() {
        for (javafx.scene.Node target : List.of(table, emptyState, foldersPane)) {
            target.setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
                e.consume();
            });
            target.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean ok = db.hasFiles();
                if (ok) {
                    runImport(db.getFiles().stream().map(File::toPath).toList());
                }
                e.setDropCompleted(ok);
                e.consume();
            });
        }
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
