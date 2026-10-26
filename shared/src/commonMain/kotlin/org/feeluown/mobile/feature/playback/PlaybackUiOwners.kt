package org.feeluown.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owns FullPlayer and queue-overlay navigation without mirroring controller state. */
class DefaultPlaybackNavigationPort : PlaybackNavigationPort {
    override var isFullPlayerOpen by mutableStateOf(false)
        private set
    override var isQueueOpen by mutableStateOf(false)
        private set

    override fun openFullPlayer() {
        isFullPlayerOpen = true
    }

    override fun closeFullPlayer() {
        isQueueOpen = false
        isFullPlayerOpen = false
    }

    override fun toggleQueue() {
        if (!isFullPlayerOpen) return
        isQueueOpen = !isQueueOpen
    }
}

/**
 * Reads rich presentation directly from the playback engine/settings owners and overlays durable
 * logical queue metadata for the active track. Platform engines may still expose legacy decorated
 * tracks during the migration; physical source identity is projected separately through
 * [resolvedSource]. A presentation-only compatibility decoration keeps existing player UI working
 * without allowing replacement metadata back into playback business state.
 */
class DefaultPlaybackPresentationPort(
    private val playbackEngine: PlaybackEngine,
    private val queuePort: PlaybackQueueUiPort,
    settingsRepository: AppSettingsRepository,
    scope: CoroutineScope,
) : PlaybackPresentationPort {
    private var playbackState by mutableStateOf(playbackEngine.state.value)
    private var settings by mutableStateOf(settingsRepository.state.value.settings)

    init {
        scope.launch {
            playbackEngine.state.collect { playbackState = it }
        }
        scope.launch {
            settingsRepository.state.collect { settings = it.settings }
        }
    }

    override val currentTrack: MusicTrack?
        get() = resolvePlaybackPresentationTrack(
            engineTrack = playbackState.currentTrack,
            queueTrack = queuePort.currentQueueTrack,
        )?.withResolvedPresentation(resolvedSource)
    override val resolvedSource: ResolvedPlaybackSource?
        get() = playbackState.resolvedSource
            ?: playbackState.currentTrack?.toLegacyResolvedPlaybackSource()
    override val playbackParts: List<PlaybackPart>
        get() = playbackState.playbackParts
    override val currentPartIndex: Int
        get() = playbackState.currentPartIndex
    override val lyricFontSize: LyricFontSize
        get() = settings.lyricFontSize
    override val themeMode: ThemeMode
        get() = settings.themeMode
    override val dynamicCoverColorEnabled: Boolean
        get() = settings.dynamicCoverColorEnabled
    override val waveformAnimationDisabled: Boolean
        get() = settings.waveformAnimationDisabled
    override val audioQuality: String?
        get() = playbackState.audioQuality
    override val audioFormatInfo: AudioFormatInfo?
        get() = playbackState.audioFormatInfo
    override val audioDecoderInfo: AudioDecoderInfo?
        get() = playbackState.audioDecoderInfo

    override fun seekTo(positionMs: Long) {
        val normalizedPosition = positionMs.coerceAtLeast(0L).let { position ->
            playbackState.durationMs.takeIf { it > 0L }?.let(position::coerceAtMost) ?: position
        }
        playbackEngine.seekTo(normalizedPosition)
    }
}

/**
 * Placeholder for platforms that do not wire the quality picker (desktop and
 * iOS today). Keeps the player chip display-only there instead of offering a
 * menu whose selection would do nothing.
 */
object UnsupportedPlaybackQualityPort : PlaybackQualityPort {
    override val isSupported: Boolean get() = false
    override val availableQualities: Set<AudioQualityPolicy>? get() = null
    override val effectiveQuality: AudioQualityPolicy? get() = null
    override fun switchQuality(quality: AudioQualityPolicy) = Unit
}

/**
 * Session-scoped quality switching for the now-playing screen.
 *
 * Picking a tier never touches the saved settings; it applies a
 * [PlaybackQualityOverride] and restarts the active track so the provider
 * re-resolves at the new tier. The platform engines cannot start at an offset,
 * so the previous position is restored by seeking once the new generation is
 * playing — that part is best effort and gives up after [SWITCH_TIMEOUT_MS].
 */
class DefaultPlaybackQualityPort(
    private val playbackState: StateFlow<PlaybackState>,
    private val availability: suspend (MusicTrack) -> Set<AudioQualityPolicy>?,
    private val restartCurrent: () -> Unit,
    private val seekTo: (Long) -> Unit,
    private val scope: CoroutineScope,
) : PlaybackQualityPort {
    private var availableQualitiesState by mutableStateOf<Set<AudioQualityPolicy>?>(null)
    private var effectiveQualityState by mutableStateOf<AudioQualityPolicy?>(null)
    private var availabilityJob: Job? = null
    private var loadedTrackId: String? = null
    private var switchJob: Job? = null

    init {
        scope.launch {
            playbackState.collect { state ->
                // What is actually playing wins; the override is only a fallback
                // for sources that never report a tier (non-NetEase) and for the
                // window between picking a tier and the re-resolve finishing.
                // PlaybackState.audioQuality is a display label by this point, so
                // it must be parsed as a label rather than as a raw level.
                effectiveQualityState = state.audioQuality
                    ?.let(AudioQualityPolicy::fromLevelOrLabel)
                    ?: PlaybackQualityOverride.level?.let(AudioQualityPolicy::fromLevel)
                val track = state.currentTrack
                if (track?.id != loadedTrackId) {
                    loadedTrackId = track?.id
                    availableQualitiesState = null
                    availabilityJob?.cancel()
                    availabilityJob = track?.let { active ->
                        scope.launch { availableQualitiesState = availability(active) }
                    }
                }
            }
        }
    }

    override val isSupported: Boolean get() = true

    override val availableQualities: Set<AudioQualityPolicy>?
        get() = availableQualitiesState

    override val effectiveQuality: AudioQualityPolicy?
        get() = effectiveQualityState

    override fun switchQuality(quality: AudioQualityPolicy) {
        // A newer pick supersedes any pending one: without this, a slow first
        // switch could seek after the second one already restored its position.
        switchJob?.cancel()
        val before = playbackState.value
        val resumePosition = before.positionMs
        PlaybackQualityOverride.apply(quality)
        effectiveQualityState = quality
        restartCurrent()
        switchJob = scope.launch {
            // Wait for the restarted generation to actually be playing: seeking
            // while the previous media item is still live would be swallowed.
            withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                playbackState.first {
                    it.playbackGeneration != before.playbackGeneration &&
                        (it.status == PlayerStatus.Playing || it.status == PlayerStatus.Paused)
                }
            } ?: return@launch
            ensureActive()
            if (resumePosition > 0L) seekTo(resumePosition)
        }
    }

    private companion object {
        const val SWITCH_TIMEOUT_MS = 5_000L
    }
}

/**
 * Resolve the logical track shown by player UI without allowing physical replacement metadata from
 * the engine to become business identity. The queue wins for the active logical identity because it
 * carries restored/local edits; engine-only states are normalized back to logical identity.
 */
internal fun resolvePlaybackPresentationTrack(
    engineTrack: MusicTrack?,
    queueTrack: MusicTrack?,
): MusicTrack? {
    val logicalEngineTrack = engineTrack?.logicalPlaybackTrack()
    val logicalQueueTrack = queueTrack?.logicalPlaybackTrack()
    return when {
        logicalEngineTrack == null -> logicalQueueTrack
        logicalQueueTrack == null -> logicalEngineTrack
        logicalEngineTrack.id == logicalQueueTrack.id -> logicalQueueTrack
        else -> logicalEngineTrack
    }
}

private fun MusicTrack.withResolvedPresentation(source: ResolvedPlaybackSource?): MusicTrack {
    val resolved = source?.takeIf { it.isReplacement } ?: return this
    return copy(
        isSmartReplacement = true,
        originalId = id,
        originalTitle = title,
        originalArtists = artists,
        originalAlbum = album,
        originalSource = this.source,
        originalProviderName = providerName,
        originalCoverUrl = coverUrl,
        replacementId = resolved.trackId,
        replacementTitle = resolved.title,
        replacementArtists = resolved.artists,
        replacementAlbum = resolved.album,
        replacementSource = resolved.source,
        replacementProviderName = resolved.providerName,
        replacementCoverUrl = resolved.coverUrl,
        replacementStrategy = resolved.replacementStrategy,
        replacementScore = resolved.replacementScore,
    )
}
