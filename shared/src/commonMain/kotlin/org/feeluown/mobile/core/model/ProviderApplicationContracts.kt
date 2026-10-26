package org.feeluown.mobile

/** Content-domain provider surface: detail/catalog reads plus library mutations only. */
interface ProviderContentRepository : ProviderCatalogRepository, ProviderLibraryRepository

/** App-owned command for applying network-specific provider audio-quality preferences. */
interface ProviderAudioQualityPort {
    suspend fun updateAudioQualityPolicies(
        wifiPolicy: AudioQualityPolicy,
        cellularPolicy: AudioQualityPolicy,
    )
}

/**
 * App-owned query for the quality tiers a specific track exists in.
 *
 * `null` means "this source cannot answer" (non-NetEase, unsigned session,
 * request failure) and must be rendered as "all tiers enabled" rather than as
 * "nothing available".
 */
interface ProviderQualityAvailabilityPort {
    suspend fun availableQualities(track: MusicTrack): Set<AudioQualityPolicy>?
}
