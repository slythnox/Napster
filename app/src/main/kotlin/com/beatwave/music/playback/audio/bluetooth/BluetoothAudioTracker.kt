/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.audio.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.lang.reflect.Method

/**
 * Realtime telemetry model capturing actual negotiated Bluetooth A2DP codec and transport state.
 */
data class BluetoothTelemetry(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val codecName: String = "Unknown",
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateLabel: String = "Not exposed by Android",
    val mode: String? = null,
    val isAuthoritative: Boolean = false,
    val lastUpdatedMs: Long = 0L,
) {
    val isHighRes: Boolean
        get() = (sampleRateHz != null && sampleRateHz > 48000) || (bitDepth != null && bitDepth > 16)

    /**
     * Whether [codecName] names a codec Android actually reported. False for placeholder
     * strings ("System Managed", "Unknown").
     */
    val hasNamedCodec: Boolean
        get() = isAuthoritative && codecName.isNotBlank() && codecName != "Unknown" && codecName != "System Managed"

    fun formattedSummary(): String = buildString {
        if (!isConnected) {
            append("Disconnected")
            return@buildString
        }
        append(codecName)
        if (bitDepth != null) append(" / $bitDepth-bit")
        if (sampleRateHz != null) append(" / $sampleRateHz Hz")
        if (!mode.isNullOrBlank()) {
            append(" ($mode)")
        } else if (bitrateLabel != "Not exposed by Android" && bitrateLabel.isNotBlank()) {
            append(" @ $bitrateLabel")
        }
    }
}

/**
 * Authoritative, non-blocking tracker monitoring Bluetooth A2DP codec and configuration changes.
 * Does not block playback if permissions are missing or APIs are unavailable.
 */
class BluetoothAudioTracker(private val context: Context) {

    private val _telemetry = MutableStateFlow(BluetoothTelemetry())
    val telemetry: StateFlow<BluetoothTelemetry> = _telemetry.asStateFlow()

    private var a2dpProfile: BluetoothA2dp? = null
    private var isReceiverRegistered: Boolean = false

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = proxy as? BluetoothA2dp
                refreshCurrentDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = null
                _telemetry.value = BluetoothTelemetry()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_CODEC_CONFIG_CHANGED -> {
                    parseCodecStatusIntent(intent)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        refreshCurrentDevice()
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        _telemetry.value = BluetoothTelemetry()
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        _telemetry.value = BluetoothTelemetry()
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        refreshCurrentDevice()
                    }
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshCurrentDevice()
                }
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_CODEC_CONFIG_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "Failed to register Bluetooth receiver")
            }
        }

        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        } catch (e: Throwable) {
            Timber.tag(TAG).w(e, "Failed to bind A2DP profile proxy")
        }

        if (a2dpProfile != null) {
            refreshCurrentDevice()
        }
    }

    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
            isReceiverRegistered = false
        }
        val proxy = a2dpProfile
        if (proxy != null) {
            try {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            } catch (_: Throwable) {
            }
            a2dpProfile = null
        }
        _telemetry.value = BluetoothTelemetry()
    }

    fun refreshCurrentDevice() {
        val a2dp = a2dpProfile ?: run {
            readAudioDeviceInfoFallback()
            return
        }

        if (!hasBluetoothConnectPermission()) {
            readAudioDeviceInfoFallback()
            return
        }

        try {
            @SuppressLint("MissingPermission")
            val connectedDevices = a2dp.connectedDevices
            val activeDevice = connectedDevices.firstOrNull()
            if (activeDevice == null) {
                readAudioDeviceInfoFallback()
                return
            }

            val deviceName = try {
                @SuppressLint("MissingPermission")
                activeDevice.name ?: activeDevice.alias ?: "Bluetooth Device"
            } catch (_: Throwable) {
                "Bluetooth Device"
            }

            val method: Method? = try {
                a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            } catch (_: Throwable) {
                null
            }

            val codecStatus = method?.invoke(a2dp, activeDevice)
            if (codecStatus != null) {
                val parsed = parseCodecStatusObject(codecStatus, deviceName)
                if (parsed != null) {
                    _telemetry.value = parsed
                    return
                }
            }

            _telemetry.value = BluetoothTelemetry(
                isConnected = true,
                deviceName = deviceName,
                codecName = "System Managed",
                bitrateLabel = "Not exposed by Android",
                isAuthoritative = false,
                lastUpdatedMs = SystemClock.elapsedRealtime(),
            )
        } catch (e: Throwable) {
            Timber.tag(TAG).d("Error refreshing A2DP device status: ${e.message}")
            readAudioDeviceInfoFallback()
        }
    }

    private fun readAudioDeviceInfoFallback() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val btDevice = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
            it.isSink && it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (btDevice != null) {
            val name = btDevice.productName?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: "Bluetooth Device"
            _telemetry.value = BluetoothTelemetry(
                isConnected = true,
                deviceName = name,
                codecName = "System Managed",
                bitrateLabel = "Not exposed by Android",
                isAuthoritative = false,
                lastUpdatedMs = SystemClock.elapsedRealtime(),
            )
        } else {
            _telemetry.value = BluetoothTelemetry()
        }
    }

    private fun parseCodecStatusIntent(intent: Intent) {
        val deviceName = if (hasBluetoothConnectPermission()) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            try {
                @SuppressLint("MissingPermission")
                device?.name ?: device?.alias ?: _telemetry.value.deviceName ?: "Bluetooth Device"
            } catch (_: Throwable) {
                _telemetry.value.deviceName ?: "Bluetooth Device"
            }
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val btDevice = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
                it.isSink && it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            btDevice?.productName?.toString()?.trim() ?: _telemetry.value.deviceName ?: "Bluetooth Device"
        }

        val codecStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CODEC_STATUS, android.bluetooth.BluetoothCodecStatus::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CODEC_STATUS)
        } ?: intent.extras?.get(EXTRA_CODEC_STATUS)

        if (codecStatus != null) {
            val parsed = parseCodecStatusObject(codecStatus, deviceName)
            if (parsed != null) {
                _telemetry.value = parsed
                return
            }
        }
        refreshCurrentDevice()
    }

    internal fun parseCodecStatusObject(codecStatus: Any, deviceName: String): BluetoothTelemetry? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && codecStatus is android.bluetooth.BluetoothCodecStatus) {
                val codecConfig = codecStatus.codecConfig ?: return null
                parseCodecConfigTyped(codecConfig, deviceName)
            } else {
                val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                val codecConfig = getCodecConfigMethod.invoke(codecStatus) ?: return null
                parseCodecConfigReflective(codecConfig, deviceName)
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).d("Failed to parse BluetoothCodecStatus: ${e.message}")
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun parseCodecConfigTyped(
        codecConfig: android.bluetooth.BluetoothCodecConfig,
        deviceName: String,
    ): BluetoothTelemetry {
        val codecType = codecConfig.codecType
        val sampleRateMask = codecConfig.sampleRate
        val bitsMask = codecConfig.bitsPerSample
        val codecSpecific1 = codecConfig.codecSpecific1

        val extendedName = if (Build.VERSION.SDK_INT >= 35) {
            runCatching { codecConfig.extendedCodecType?.codecName }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val codecName: String = extendedName ?: try {
            val nameMethod = codecConfig.javaClass.getMethod("getCodecName")
            (nameMethod.invoke(codecConfig) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } ?: mapCodecType(codecType)

        val sampleRateHz = mapSampleRate(sampleRateMask)
        val bitDepth = mapBitDepth(bitsMask)
        val mode = when (codecType) {
            SOURCE_CODEC_TYPE_LDAC -> parseLdacQualityMode(codecSpecific1)
            else -> null
        }

        return BluetoothTelemetry(
            isConnected = true,
            deviceName = deviceName,
            codecName = codecName,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateLabel = "Not exposed by Android",
            mode = mode,
            isAuthoritative = true,
            lastUpdatedMs = SystemClock.elapsedRealtime(),
        )
    }

    internal fun parseCodecConfigReflective(
        codecConfig: Any,
        deviceName: String,
    ): BluetoothTelemetry? {
        return try {
            val clazz = codecConfig.javaClass
            val codecType = (clazz.getMethod("getCodecType").invoke(codecConfig) as? Number)?.toInt() ?: -1
            val sampleRateMask = (clazz.getMethod("getSampleRate").invoke(codecConfig) as? Number)?.toInt() ?: 0
            val bitsMask = (clazz.getMethod("getBitsPerSample").invoke(codecConfig) as? Number)?.toInt() ?: 0
            val codecSpecific1 = (clazz.getMethod("getCodecSpecific1").invoke(codecConfig) as? Number)?.toLong() ?: 0L

            val codecName = try {
                val nameMethod = clazz.getMethod("getCodecName")
                (nameMethod.invoke(codecConfig) as? String)?.takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                null
            } ?: mapCodecType(codecType)

            val sampleRateHz = mapSampleRate(sampleRateMask)
            val bitDepth = mapBitDepth(bitsMask)
            val mode = when (codecType) {
                SOURCE_CODEC_TYPE_LDAC -> parseLdacQualityMode(codecSpecific1)
                else -> null
            }

            BluetoothTelemetry(
                isConnected = true,
                deviceName = deviceName,
                codecName = codecName,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                bitrateLabel = "Not exposed by Android",
                mode = mode,
                isAuthoritative = true,
                lastUpdatedMs = SystemClock.elapsedRealtime(),
            )
        } catch (e: Throwable) {
            Timber.tag(TAG).d("Reflective parse failed: ${e.message}")
            null
        }
    }

    internal fun mapCodecType(type: Int): String = when (type) {
        SOURCE_CODEC_TYPE_SBC -> "SBC"
        SOURCE_CODEC_TYPE_AAC -> "AAC"
        SOURCE_CODEC_TYPE_APTX -> "aptX"
        SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
        SOURCE_CODEC_TYPE_LDAC -> "LDAC"
        SOURCE_CODEC_TYPE_LC3 -> "LC3"
        SOURCE_CODEC_TYPE_OPUS -> "Opus"
        else -> "Unknown"
    }

    internal fun mapSampleRate(mask: Int): Int? = when {
        mask and SAMPLE_RATE_192000 != 0 -> 192000
        mask and SAMPLE_RATE_176400 != 0 -> 176400
        mask and SAMPLE_RATE_96000 != 0 -> 96000
        mask and SAMPLE_RATE_88200 != 0 -> 88200
        mask and SAMPLE_RATE_48000 != 0 -> 48000
        mask and SAMPLE_RATE_44100 != 0 -> 44100
        else -> null
    }

    internal fun mapBitDepth(mask: Int): Int? = when {
        mask and BITS_PER_SAMPLE_32 != 0 -> 32
        mask and BITS_PER_SAMPLE_24 != 0 -> 24
        mask and BITS_PER_SAMPLE_16 != 0 -> 16
        else -> null
    }

    internal fun parseLdacQualityMode(codecSpecific1: Long): String? {
        val mode = (codecSpecific1 and 0xFFFF).toInt()
        return when (mode) {
            1000 -> "990 kbps (High)"
            1001 -> "660 kbps (Standard)"
            1002 -> "330 kbps (Connection)"
            1003 -> "Adaptive Bitrate"
            else -> null
        }
    }

    companion object {
        private const val TAG = "BluetoothAudioTracker"

        const val ACTION_CODEC_CONFIG_CHANGED = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
        const val EXTRA_CODEC_STATUS = "android.bluetooth.extra.CODEC_STATUS"

        const val SOURCE_CODEC_TYPE_SBC = 0
        const val SOURCE_CODEC_TYPE_AAC = 1
        const val SOURCE_CODEC_TYPE_APTX = 2
        const val SOURCE_CODEC_TYPE_APTX_HD = 3
        const val SOURCE_CODEC_TYPE_LDAC = 4
        const val SOURCE_CODEC_TYPE_LC3 = 5
        const val SOURCE_CODEC_TYPE_OPUS = 6

        const val SAMPLE_RATE_44100 = 0x1
        const val SAMPLE_RATE_48000 = 0x2
        const val SAMPLE_RATE_88200 = 0x4
        const val SAMPLE_RATE_96000 = 0x8
        const val SAMPLE_RATE_176400 = 0x10
        const val SAMPLE_RATE_192000 = 0x20

        const val BITS_PER_SAMPLE_16 = 0x1
        const val BITS_PER_SAMPLE_24 = 0x2
        const val BITS_PER_SAMPLE_32 = 0x4
    }
}
