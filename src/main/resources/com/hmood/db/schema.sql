-- =====================================================================
-- H MOOD — schéma SQLite (idempotent : exécuté à chaque démarrage)
-- Les dates sont stockées en UTC (CURRENT_TIMESTAMP).
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    display_name TEXT    NOT NULL,
    created_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Utilisateur local unique pour le MVP (multi-profils prévu plus tard)
INSERT OR IGNORE INTO users (id, display_name) VALUES (1, 'Mélomane');

CREATE TABLE IF NOT EXISTS artists (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT    NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS albums (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    title     TEXT    NOT NULL COLLATE NOCASE,
    artist_id INTEGER REFERENCES artists (id) ON DELETE SET NULL,
    year      INTEGER,
    UNIQUE (title, artist_id)
);
CREATE INDEX IF NOT EXISTS idx_albums_artist ON albums (artist_id);

CREATE TABLE IF NOT EXISTS tracks (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path      TEXT    NOT NULL UNIQUE,
    title          TEXT    NOT NULL,
    artist_id      INTEGER REFERENCES artists (id) ON DELETE SET NULL,
    album_id       INTEGER REFERENCES albums (id) ON DELETE SET NULL,
    genre          TEXT,
    year           INTEGER,
    duration_ms    INTEGER NOT NULL DEFAULT 0,
    mood           TEXT,
    cover_path     TEXT,
    play_count     INTEGER NOT NULL DEFAULT 0,
    last_played_at TEXT,
    added_at       TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks (artist_id);
CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks (album_id);
CREATE INDEX IF NOT EXISTS idx_tracks_mood ON tracks (mood);
CREATE INDEX IF NOT EXISTS idx_tracks_genre ON tracks (genre);

CREATE TABLE IF NOT EXISTS playlists (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    description TEXT,
    mood        TEXT,
    created_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_playlists_user ON playlists (user_id);

CREATE TABLE IF NOT EXISTS playlist_tracks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    playlist_id INTEGER NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    track_id    INTEGER NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    added_at    TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_order ON playlist_tracks (playlist_id, position);
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_track ON playlist_tracks (track_id);

CREATE TABLE IF NOT EXISTS favorites (
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id   INTEGER NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    created_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, track_id)
);
CREATE INDEX IF NOT EXISTS idx_favorites_track ON favorites (track_id);

CREATE TABLE IF NOT EXISTS listening_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    track_id    INTEGER NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    played_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    listened_ms INTEGER NOT NULL DEFAULT 0,
    completed   INTEGER NOT NULL DEFAULT 0 CHECK (completed IN (0, 1))
);
CREATE INDEX IF NOT EXISTS idx_history_user_date ON listening_history (user_id, played_at);
CREATE INDEX IF NOT EXISTS idx_history_track ON listening_history (track_id);

CREATE TABLE IF NOT EXISTS mood_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mood       TEXT    NOT NULL,
    source     TEXT    NOT NULL DEFAULT 'manual',
    created_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mood_history_user_date ON mood_history (user_id, created_at);

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pref_key   TEXT    NOT NULL,
    pref_value TEXT,
    updated_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, pref_key)
);

-- Cache des catalogues en ligne (classements, recherches) : source et date conservées
CREATE TABLE IF NOT EXISTS catalog_cache (
    cache_key  TEXT PRIMARY KEY,
    provider   TEXT NOT NULL,
    payload    TEXT NOT NULL,
    fetched_at TEXT NOT NULL
);

-- Tempo (BPM) et énergie estimés pour les morceaux en ligne (aucun audio n'est conservé)
CREATE TABLE IF NOT EXISTS track_analysis (
    cache_key   TEXT PRIMARY KEY,
    bpm         REAL NOT NULL,
    energy      REAL NOT NULL,
    method      TEXT NOT NULL,
    analyzed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Morceaux enregistrés hors ligne (Jamendo uniquement, téléchargement autorisé par l'artiste)
CREATE TABLE IF NOT EXISTS offline_downloads (
    provider      TEXT NOT NULL,
    external_id   TEXT NOT NULL,
    file_path     TEXT NOT NULL,
    title         TEXT,
    artist        TEXT,
    license       TEXT,
    license_url   TEXT,
    downloaded_at TEXT NOT NULL,
    PRIMARY KEY (provider, external_id)
);

-- Paroles (LRCLIB) mises en cache
CREATE TABLE IF NOT EXISTS lyrics_cache (
    cache_key    TEXT PRIMARY KEY,
    synced       TEXT,
    plain        TEXT,
    instrumental INTEGER NOT NULL DEFAULT 0,
    fetched_at   TEXT NOT NULL
);

-- Écoutes en streaming (Deezer, Apple, Audius, Jamendo), gardées en local pour le bilan mensuel
CREATE TABLE IF NOT EXISTS stream_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    TEXT    NOT NULL,
    external_id TEXT,
    title       TEXT    NOT NULL,
    artist      TEXT,
    genre       TEXT,
    cover_url   TEXT,
    mood        TEXT,
    played_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    listened_ms INTEGER NOT NULL DEFAULT 0,
    completed   INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_stream_history_user_date ON stream_history (user_id, played_at);
