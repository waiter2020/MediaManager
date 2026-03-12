package com.mediamanager.metadata.spi;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MetadataResult {
    
    // Core details
    private String title;
    private String originalTitle;
    private String sortTitle;
    private LocalDate releaseDate;
    private Double rating;
    private String overview;
    private String posterPath;
    private String backdropPath;
    
    // TV Specific
    private String network;
    private String status; // Continuing, Ended
    private Integer seasonNumber;
    private Integer episodeNumber;
    
    // Movie/Episode Specific
    private Integer runtimeMinutes;
    private String certification;
    
    // Audio Specific
    private String artist;
    private String album;
    private String albumArtist;
    private Integer trackNumber;
    private Integer discNumber;

    // Collections
    private List<String> genres;
    private List<String> studios;
    
    // Cast/Crew (JSON strings or complex objects later converted to JSON)
    private String castInfo; 
    private String crew;

    // Additional identifiers
    @Builder.Default
    private Map<String, String> providerIds = new HashMap<>(); // e.g. {"tmdb": "12345", "imdb": "tt1234567"}
    
    public void mergeFrom(MetadataResult other) {
        if (other == null) return;

        if (this.title == null && other.title != null) this.title = other.title;
        if (this.originalTitle == null && other.originalTitle != null) this.originalTitle = other.originalTitle;
        if (this.sortTitle == null && other.sortTitle != null) this.sortTitle = other.sortTitle;
        if (this.releaseDate == null && other.releaseDate != null) this.releaseDate = other.releaseDate;
        if (this.rating == null && other.rating != null) this.rating = other.rating;
        if (this.overview == null && other.overview != null) this.overview = other.overview;
        if (this.posterPath == null && other.posterPath != null) this.posterPath = other.posterPath;
        if (this.backdropPath == null && other.backdropPath != null) this.backdropPath = other.backdropPath;
        if (this.network == null && other.network != null) this.network = other.network;
        if (this.status == null && other.status != null) this.status = other.status;
        if (this.seasonNumber == null && other.seasonNumber != null) this.seasonNumber = other.seasonNumber;
        if (this.episodeNumber == null && other.episodeNumber != null) this.episodeNumber = other.episodeNumber;
        if (this.runtimeMinutes == null && other.runtimeMinutes != null) this.runtimeMinutes = other.runtimeMinutes;
        if (this.certification == null && other.certification != null) this.certification = other.certification;
        if (this.artist == null && other.artist != null) this.artist = other.artist;
        if (this.album == null && other.album != null) this.album = other.album;
        if (this.albumArtist == null && other.albumArtist != null) this.albumArtist = other.albumArtist;
        if (this.trackNumber == null && other.trackNumber != null) this.trackNumber = other.trackNumber;
        if (this.discNumber == null && other.discNumber != null) this.discNumber = other.discNumber;
        if ((this.genres == null || this.genres.isEmpty()) && other.genres != null && !other.genres.isEmpty()) {
            this.genres = other.genres;
        }
        if ((this.studios == null || this.studios.isEmpty()) && other.studios != null && !other.studios.isEmpty()) {
            this.studios = other.studios;
        }
        if (this.castInfo == null && other.castInfo != null) this.castInfo = other.castInfo;
        if (this.crew == null && other.crew != null) this.crew = other.crew;
        if (other.providerIds != null && !other.providerIds.isEmpty()) {
            this.providerIds.putAll(other.providerIds);
        }
    }
}
