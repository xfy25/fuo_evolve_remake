package org.feeluown.mobile

private const val AUDIO_QUALITY_LQ_MAX_BITRATE = 128_000L
private const val AUDIO_QUALITY_HQ_MIN_BITRATE = 256_000L

private val LOSSLESS_AUDIO_MARKERS = listOf(
    "flac",
    "alac",
    "ape",
    "audio/wav",
    "audio/x-wav",
    "audio/raw",
    "pcm",
    "lossless",
)

/**
 * MeloX-style quality tiers. Providers that speak the MeloX quality model
 * (currently NetEase) report the tier the server actually returned, and the
 * player chip shows that tier name verbatim.
 */
private val AUDIO_QUALITY_LEVEL_LABELS = mapOf(
    "standard" to "标准",
    "exhigh" to "高品质",
    "lossless" to "无损",
    "hires" to "Hi-Res",
)

private val PROVIDER_AUDIO_QUALITY_LABELS = mapOf(
    "shq" to "SHQ",
    "hq" to "HQ",
    "hq<>" to "HQ",
    "sq" to "SQ",
    "sq<>" to "SQ",
    "lq" to "LQ",
    "lq<>" to "LQ",
    // YouTube / YouTube Music InnerTube audioQuality values. Prefer these
    // codec-aware provider tiers over comparing AAC/Opus bitrates to MP3.
    "audio_quality_high" to "HQ",
    "audio_quality_medium" to "SQ",
    "audio_quality_low" to "LQ",
    "audio_quality_ultralow" to "LQ",
)

/**
 * Converts provider-specific quality values into the small set of quality tiers
 * shown by the player. Codec/container names stay in [AudioFormatInfo] and must
 * not leak into the quality chip.
 *
 * Provider-declared tiers take precedence over generic bitrate thresholds. This
 * matters for efficient codecs such as AAC and Opus, where e.g. a medium-quality
 * stream may use a substantially lower bitrate than an MP3 stream of similar
 * perceptual quality.
 *
 * When a provider does not expose a quality tier, bitrate remains a conservative
 * fallback: 128 kbps and below is LQ, 129-255 kbps is SQ, 256 kbps and above is
 * HQ, while known lossless formats are SHQ.
 *
 * Hi-Res is deliberately not inferred here because bitrate/codec alone cannot
 * establish the required sample rate and bit depth.
 */
fun normalizedAudioQualityLabel(
    rawQuality: String?,
    formatInfo: AudioFormatInfo?,
): String? {
    val raw = rawQuality?.trim()?.takeIf(String::isNotEmpty)
    val normalizedRaw = raw?.lowercase()

    // Checked first: "lossless" would otherwise be caught by the lossless
    // markers below and reported as "SHQ" instead of the MeloX tier name.
    normalizedRaw?.let(AUDIO_QUALITY_LEVEL_LABELS::get)?.let { return it }

    if (isLosslessAudio(normalizedRaw, formatInfo)) return "SHQ"

    normalizedRaw?.let(PROVIDER_AUDIO_QUALITY_LABELS::get)?.let { return it }

    val bitrate = formatInfo?.averageBitrate?.takeIf { it > 0 }
        ?: normalizedRaw?.toLongOrNull()?.takeIf { it > 0 }
        ?: formatInfo?.peakBitrate?.takeIf { it > 0 }

    return audioQualityLabelForBitrate(bitrate)
}

fun audioQualityLabelForBitrate(bitrate: Long?): String? {
    if (bitrate == null || bitrate <= 0) return null
    return when {
        bitrate <= AUDIO_QUALITY_LQ_MAX_BITRATE -> "LQ"
        bitrate < AUDIO_QUALITY_HQ_MIN_BITRATE -> "SQ"
        else -> "HQ"
    }
}

private fun isLosslessAudio(rawQuality: String?, formatInfo: AudioFormatInfo?): Boolean {
    val description = buildList {
        rawQuality?.let(::add)
        formatInfo?.format?.lowercase()?.let(::add)
        formatInfo?.codec?.lowercase()?.let(::add)
    }.joinToString(" ")
    return LOSSLESS_AUDIO_MARKERS.any(description::contains)
}
