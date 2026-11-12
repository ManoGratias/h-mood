package com.hmood.service;

import com.hmood.model.Mood;
import com.hmood.model.Playlist;
import com.hmood.model.Track;
import com.hmood.recommendation.PlaylistCriteria;
import com.hmood.recommendation.RecommendationEngine;
import com.hmood.repository.PlaylistRepository;
import com.hmood.repository.TrackRepository;

import java.util.List;
import java.util.Optional;

/** Création, édition et génération de playlists. */
public final class PlaylistService {

    private final PlaylistRepository playlists;
    private final TrackRepository tracks;
    private final RecommendationEngine engine;

    public PlaylistService(PlaylistRepository playlists, TrackRepository tracks, RecommendationEngine engine) {
        this.playlists = playlists;
        this.tracks = tracks;
        this.engine = engine;
    }

    public List<Playlist> all() {
        return playlists.findAll();
    }

    public Optional<Playlist> find(long id) {
        return playlists.findById(id);
    }

    public long create(String name, String description, Mood mood) {
        return playlists.create(requireName(name), description, mood);
    }

    public long createWithTracks(String name, String description, Mood mood, List<Track> content) {
        long id = create(name, description, mood);
        playlists.addTracks(id, content.stream().map(Track::id).toList());
        return id;
    }

    public void rename(long id, String name) {
        playlists.rename(id, requireName(name));
    }

    public void delete(long id) {
        playlists.delete(id);
    }

    public List<Track> tracks(long playlistId) {
        return tracks.findByPlaylist(playlistId);
    }

    public void addTracks(long playlistId, List<Track> content) {
        playlists.addTracks(playlistId, content.stream().map(Track::id).toList());
    }

    public void replaceTracks(long playlistId, List<Track> content) {
        playlists.replaceTracks(playlistId, content.stream().map(Track::id).toList());
    }

    public List<Track> generate(PlaylistCriteria criteria) {
        return engine.generate(criteria);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la playlist est obligatoire.");
        }
        return name.trim();
    }
}
