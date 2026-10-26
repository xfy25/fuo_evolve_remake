package org.feeluown.mobile

/**
 * Session-scoped quality override set by the now-playing quality picker.
 *
 * Deliberately NOT persisted: picking a tier in the player must leave the saved
 * Wi-Fi/cellular preferences untouched. The override lives for the process,
 * survives track changes, and is cleared as soon as the settings screen applies
 * a saved policy, so the settings value always wins once it changes.
 *
 * Known side effect: downloads resolved during the same session inherit this
 * override, because both paths share [KotlinProviderRepository.resolveTrack].
 */
object PlaybackQualityOverride {
    @Volatile
    private var overrideLevel: String? = null

    val level: String?
        get() = overrideLevel

    fun apply(policy: AudioQualityPolicy) {
        overrideLevel = policy.level
    }

    fun clear() {
        overrideLevel = null
    }
}
