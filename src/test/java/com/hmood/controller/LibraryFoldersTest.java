package com.hmood.controller;

import com.hmood.model.Track;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LibraryFoldersTest {

    private static Track local(String path) {
        return new Track(1, path, "t", null, null, null, null, 0, null, null, 0, false);
    }

    @Test
    void groupsByParentFolder() {
        Path folder = Path.of("musique", "Sound of love");
        assertEquals(folder.toString(), LibraryController.folderOf(local(folder.resolve("19 Heaven.mp3").toString())));
        assertEquals("Sound of love", LibraryController.folderName(folder.toString()));
    }

    @Test
    void namesTheOfflineFolder() {
        assertEquals("Titres hors ligne",
                LibraryController.folderName(Path.of("AppData", "Roaming", "HMood", "hors-ligne").toString()));
        assertEquals("hors-ligne", LibraryController.folderName(Path.of("musique", "hors-ligne").toString()));
    }

    @Test
    void ignoresTracksWithoutLocalFile() {
        assertNull(LibraryController.folderOf(local(null)));
        assertNull(LibraryController.folderOf(
                new Track(-5, "https://cdn.example/x.mp3", "t", null, null, null, null, 0, null, null, 0, false)));
        assertNull(LibraryController.folderOf(local("bad\u0000path")));
    }
}
