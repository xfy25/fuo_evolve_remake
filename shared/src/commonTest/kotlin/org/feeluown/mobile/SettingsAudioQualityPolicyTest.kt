package org.feeluown.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsAudioQualityPolicyTest {
    @Test
    fun savedWifiAndCellularPoliciesAreAppliedTogether() = runTest {
        val settings = AppSettings(
            wifiAudioQualityPolicy = AudioQualityPolicy.HiRes,
            cellularAudioQualityPolicy = AudioQualityPolicy.Standard,
        )
        var applied: Pair<AudioQualityPolicy, AudioQualityPolicy>? = null

        applySavedAudioQualityPolicies(
            loadSettings = { settings },
            applyPolicies = { wifi, cellular -> applied = wifi to cellular },
        )

        assertEquals(AudioQualityPolicy.HiRes to AudioQualityPolicy.Standard, applied)
    }
}
