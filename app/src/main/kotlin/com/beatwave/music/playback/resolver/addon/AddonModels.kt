/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.resolver.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Manifest declaration describing an HTTP streaming add-on's identity, capabilities, and settings.
 */
@Serializable
data class AddonManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("resources") val resources: List<String> = emptyList(),
    @SerialName("settings") val settings: List<AddonSetting> = emptyList(),
    @SerialName("allowDownloads") val allowDownloads: JsonElement? = null,
    @SerialName("checkValidLossless") val checkValidLossless: JsonElement? = null,
) {
    val downloadsAllowed: Boolean get() = allowDownloads.asFlag() ?: true
    val requiresLosslessOutput: Boolean get() = checkValidLossless.asFlag() ?: false

    fun declares(resource: String): Boolean =
        resources.any { it.equals(resource, ignoreCase = true) }

    val isPlayable: Boolean get() = resources.isEmpty() || declares("search")
    val displayName: String get() = name.ifBlank { id }
}

@Serializable
data class AddonSetting(
    @SerialName("key") val key: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<AddonSettingOption> = emptyList(),
) {
    val defaultValue: String? get() = default?.asQueryValue()
}

@Serializable
data class AddonSettingOption(
    @SerialName("value") val value: JsonElement? = null,
) {
    val stringValue: String? get() = value?.asQueryValue()
}

@Serializable
data class AddonSearchResponse(
    @SerialName("tracks") val tracks: List<AddonTrack> = emptyList(),
)

@Serializable
data class AddonTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    @SerialName("duration") val duration: Double? = null,
    @SerialName("artworkURL") val artworkURL: String? = null,
    @SerialName("albumArtworkURL") val albumArtworkURL: String? = null,
    @SerialName("format") val format: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    @SerialName("atmos") val atmos: Boolean? = null,
    @SerialName("streamURL") val streamURL: String? = null,
) {
    val durationSec: Int? get() = duration?.takeIf { it > 0 }?.toInt()
    val artwork: String? get() = artworkURL?.ifBlank { null } ?: albumArtworkURL?.ifBlank { null }

    val isDolbyAtmos: Boolean
        get() = atmos == true || ATMOS_HINT.containsMatchIn(
            "$audioQuality ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} $format"
        )

    val isHiRes: Boolean get() = HI_RES_HINT.containsMatchIn("$audioQuality $format")
}

private val HI_RES_HINT = Regex("""hi[-_ ]?res|24[-_ ]?bit""", RegexOption.IGNORE_CASE)
private val ATMOS_HINT = Regex("""atmos|dolby|eac3[_-]?joc|ec-?3""", RegexOption.IGNORE_CASE)

@Serializable
data class AddonStream(
    @SerialName("url") val url: String = "",
    @SerialName("format") val format: String = "",
    @SerialName("quality") val quality: String = "",
    @SerialName("streamQuality") val streamQuality: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("codec") val codec: String? = null,
    @SerialName("fileCodec") val fileCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("containerFormat") val containerFormat: String? = null,
    @SerialName("manifest") val manifest: String? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("encrypted") val encrypted: JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    @SerialName("bitrate") val bitrate: Double? = null,
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    @SerialName("error") val error: String? = null,
) {
    val statedCodec: String?
        get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase()

    val statedContainer: String?
        get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })?.lowercase()

    val qualityText: String get() = "$quality $streamQuality $audioQuality $format"

    val isEncrypted: Boolean
        get() {
            val element = encrypted as? JsonPrimitive ?: return false
            element.booleanOrNull?.let { return it }
            return element.content.isNotBlank() &&
                !element.content.equals("false", ignoreCase = true) &&
                !element.content.equals("none", ignoreCase = true)
        }

    val transport: String?
        get() {
            val stated = manifest?.ifBlank { null }
                ?: mediaType?.ifBlank { null }
                ?: format.ifBlank { null }
                ?: return null
            return when (stated.lowercase()) {
                "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HLS
                "dash", "mpd", "application/dash+xml" -> DASH
                else -> null
            }
        }

    val sampleRateHz: Int?
        get() = sampleRate?.takeIf { it > 0 }?.let {
            if (it < 1000) (it * 1000).toInt() else it.toInt()
        } ?: KHZ_LABEL.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it > 0 }?.let { (it * 1000).toInt() }

    val bits: Int?
        get() = bitDepth?.takeIf { it > 0 }?.toInt()
            ?: BIT_DEPTH_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 8..32 }

    val kbps: Int?
        get() = bitrate?.takeIf { it > 0 }?.let { if (it > 3_000) (it / 1000).toInt() else it.toInt() }
            ?: KBPS_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()

    val isDolbyAtmos: Boolean
        get() = ATMOS_HINT.containsMatchIn(
            "$qualityText ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} ${statedCodec.orEmpty()}"
        )

    companion object {
        const val HLS = "hls"
        const val DASH = "dash"

        private val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
        private val KHZ_LABEL = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
        private val BIT_DEPTH_LABEL = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
    }
}

internal fun JsonElement?.asFlag(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    primitive.booleanOrNull?.let { return it }
    return when (primitive.content.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> primitive.content.toDoubleOrNull()?.let { it != 0.0 }
    }
}

private fun JsonElement.asQueryValue(): String? {
    if (this is JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.ifBlank { null }
}

val AddonJson = kotlinx.serialization.json.Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

