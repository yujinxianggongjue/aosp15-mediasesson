package com.example.mymediasessiontest

import android.media.MediaMetadata
import android.media.session.PlaybackState

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0,
    val position: Long = 0,
    val isPlaying: Boolean = false,
    val albumArtBytes: ByteArray? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaInfo

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (duration != other.duration) return false
        if (position != other.position) return false
        if (isPlaying != other.isPlaying) return false
        if (albumArtBytes != null) {
            if (other.albumArtBytes == null) return false
            if (!albumArtBytes.contentEquals(other.albumArtBytes)) return false
        } else if (other.albumArtBytes != null) return false
        if (lyrics != other.lyrics) return false
        if (currentLyricIndex != other.currentLyricIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (albumArtBytes?.contentHashCode() ?: 0)
        result = 31 * result + lyrics.hashCode()
        result = 31 * result + currentLyricIndex
        return result
    }

    companion object {
        fun fromMetadata(
            metadata: MediaMetadata?, 
            playbackState: PlaybackState?, 
            albumArtBytes: ByteArray?,
            lyrics: List<LyricLine> = emptyList(),
            currentLyricIndex: Int = -1
        ): MediaInfo {
            if (metadata == null) return MediaInfo(lyrics = lyrics, currentLyricIndex = currentLyricIndex)
            
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            
            val position = playbackState?.position ?: 0
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            
            return MediaInfo(
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                position = position,
                isPlaying = isPlaying,
                albumArtBytes = albumArtBytes,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex
            )
        }
    }
}