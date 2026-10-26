package org.feeluown.mobile

import kotlinx.serialization.Serializable

/**
 * Quality tier passed to providers as a request hint.
 *
 * [level] mirrors the provider-native level strings used by MeloX
 * (`standard`/`exhigh`/`lossless`/`hires`); NetEase's player URL v1 endpoint
 * takes this value verbatim.
 *
 * Highest first: [fallbackLevels] mirrors MeloX `MusicQuality.playbackFallbacks`
 * so a request for a tier the account cannot play degrades one step at a time
 * instead of jumping straight to the lowest tier.
 *
 * Declaration order is load-bearing: it must stay ascending by quality because
 * providers compare tiers with [Enum.ordinal] when picking the best granted
 * tier. Do not reorder these constants.
 */
@Serializable
enum class AudioQualityPolicy(
    val level: String,
    val label: String,
) {
    Standard("standard", "标准"),
    High("exhigh", "高品质"),
    Lossless("lossless", "无损"),
    HiRes("hires", "Hi-Res");

    val fallbackLevels: List<AudioQualityPolicy>
        get() = when (this) {
            Standard -> listOf(Standard)
            High -> listOf(High, Standard)
            Lossless -> listOf(Lossless, High, Standard)
            HiRes -> listOf(HiRes, Lossless, High, Standard)
        }

    companion object {
        fun fromLevel(level: String?): AudioQualityPolicy? =
            entries.firstOrNull { it.level == level }

        /**
         * Resolves a tier from either the provider-native level or the display
         * label produced by `normalizedAudioQualityLabel`.
         *
         * `PlaybackState.audioQuality` is normalized before it reaches the UI, so
         * the player picker only ever sees labels like "无损" / "Hi-Res"; parsing
         * them as raw levels always fails and made the menu mark the requested
         * tier instead of the one actually playing.
         */
        fun fromLevelOrLabel(value: String?): AudioQualityPolicy? =
            fromLevel(value)
                ?: entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class UnavailablePlaybackPolicy(
    val label: String,
) {
    SmartReplace("智能替换"),
    Skip("跳过"),
}

@Serializable
enum class RepeatMode(
    val label: String,
) {
    OFF("不循环"),
    QUEUE("全部循环"),
    SINGLE("单曲循环"),
}

@Serializable
data class SmartReplacementSelection(
    val replacementId: String,
    val replacementTitle: String,
    val replacementArtists: String,
    val replacementAlbum: String = "",
    val replacementSource: String,
    val replacementProviderName: String? = null,
    val replacementCoverUrl: String? = null,
    val replacementDurationMs: Long? = null,
    val replacementScore: Double = 0.0,
)

val DEFAULT_WIFI_AUDIO_QUALITY_POLICY = AudioQualityPolicy.HiRes
val DEFAULT_CELLULAR_AUDIO_QUALITY_POLICY = AudioQualityPolicy.HiRes
val DEFAULT_UNAVAILABLE_PLAYBACK_POLICY = UnavailablePlaybackPolicy.SmartReplace
const val DEFAULT_SMART_REPLACEMENT_MIN_SCORE = 0.55
const val DEFAULT_PAUSE_ON_OTHER_APP_PLAYBACK = true
const val SLEEP_TIMER_MIN_MINUTES = 1
const val SLEEP_TIMER_MAX_MINUTES = 1_440
val SLEEP_TIMER_PRESET_MINUTES = listOf(15, 30, 45, 60, 90, 120)

data class PlaybackPart(
    val id: String,
    val title: String,
    val durationMs: Long? = null,
)

data class PlaybackPayload(
    val url: String,
    val title: String,
    val artists: String,
    val album: String,
    val source: String,
    val headers: Map<String, String> = emptyMap(),
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    val lyrics: String? = null,
    val audioQuality: String? = null,
    val providerName: String? = null,
    val isSmartReplacement: Boolean = false,
    val originalId: String? = null,
    val originalTitle: String? = null,
    val originalArtists: String? = null,
    val originalAlbum: String? = null,
    val originalSource: String? = null,
    val originalProviderName: String? = null,
    val originalCoverUrl: String? = null,
    val replacementId: String? = null,
    val replacementTitle: String? = null,
    val replacementArtists: String? = null,
    val replacementAlbum: String? = null,
    val replacementSource: String? = null,
    val replacementProviderName: String? = null,
    val replacementCoverUrl: String? = null,
    val replacementStrategy: String? = null,
    val replacementScore: Double? = null,
    val parts: List<PlaybackPart> = emptyList(),
    val currentPartIndex: Int = -1,
)

enum class PlayerStatus {
    Idle,
    Loading,
    Playing,
    Paused,
    Error,
    Ended,
}

enum class SleepTimerMode {
    Off,
    Duration,
    EndOfTrack,
}

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.Off,
    val deadlineMs: Long? = null,
    val targetTrackId: String? = null,
    val remainingMs: Long? = null,
)

enum class TrackChangeDirection {
    Next,
    Previous,
}

enum class PlayMode {
    ListLoop,
    SingleLoop,
}

enum class AudioDecoderType {
    Hardware,
    Software,
}

data class AudioDecoderInfo(
    val type: AudioDecoderType,
    val name: String,
)

data class AudioFormatInfo(
    val format: String? = null,
    val codec: String? = null,
    val averageBitrate: Long? = null,
    val peakBitrate: Long? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    /** Bits per sample; parsed from FLAC STREAMINFO or derived from PCM encoding. */
    val bitDepth: Int? = null,
)
