/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.beatwave.music.playback.audio.bluetooth.BluetoothAudioTracker
import com.beatwave.music.playback.audio.bluetooth.BluetoothTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Output route information and truth-first audio transmission classification.
 */
data class OutputRouteState(
    val kind: OutputKind = OutputKind.SPEAKER,
    val deviceName: String = "Built-in Speaker",
    val routeClassification: String = "Internal Speaker",
    val bluetoothTelemetry: BluetoothTelemetry? = null,
    val isCapableOfLosslessOrHighBitrate: Boolean = false,
)

enum class OutputKind {
    SPEAKER,
    WIRED,
    USB,
    HDMI,
    DOCK,
    BLUETOOTH,
    UNKNOWN
}

/**
 * Tracks connected audio output sinks via AudioManager and BluetoothAudioTracker.
 */
class OutputRouteTracker(private val context: Context) {

    private val _routeState = MutableStateFlow(OutputRouteState())
    val routeState: StateFlow<OutputRouteState> = _routeState.asStateFlow()

    private var audioManager: AudioManager? = null
    private var bluetoothTracker: BluetoothAudioTracker? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var btTelemetryJob: kotlinx.coroutines.Job? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        if (audioManager != null) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        manager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        val bt = BluetoothAudioTracker(context)
        bluetoothTracker = bt
        bt.start()

        btTelemetryJob = scope.launch {
            bt.telemetry.collect {
                refresh()
            }
        }
        refresh()
    }

    fun stop() {
        btTelemetryJob?.cancel()
        btTelemetryJob = null
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        audioManager = null
        bluetoothTracker?.stop()
        bluetoothTracker = null
    }

    fun refresh() {
        val manager = audioManager ?: return
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val outputTypes = devices.map { it.type }.toSet()
        val btTelemetry = bluetoothTracker?.telemetry?.value

        val next = evaluate(devices, outputTypes, btTelemetry)
        if (next != _routeState.value) {
            Timber.tag(TAG).d("Audio route changed: ${next.routeClassification} (${next.deviceName})")
            _routeState.value = next
        }
    }

    internal fun evaluate(
        devices: List<AudioDeviceInfo>,
        outputTypes: Set<Int>,
        btTelemetry: BluetoothTelemetry?,
    ): OutputRouteState {
        val wiredDevice = devices.firstOrNull { it.type in WIRED }
        if (wiredDevice != null) {
            val name = wiredDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Audio"
            return OutputRouteState(
                kind = OutputKind.WIRED,
                deviceName = name,
                routeClassification = "Wired Line/Headset (System-resampled)",
                isCapableOfLosslessOrHighBitrate = true,
            )
        }

        val usbDevice = devices.firstOrNull { it.type in USB }
        if (usbDevice != null) {
            val name = usbDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC / Audio"
            return OutputRouteState(
                kind = OutputKind.USB,
                deviceName = name,
                routeClassification = "USB Audio Out (Direct / Mixer)",
                isCapableOfLosslessOrHighBitrate = true,
            )
        }

        val hdmiDevice = devices.firstOrNull { it.type in HDMI }
        if (hdmiDevice != null) {
            val name = hdmiDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "HDMI Display"
            return OutputRouteState(
                kind = OutputKind.HDMI,
                deviceName = name,
                routeClassification = "HDMI / Digital Multi-Channel",
                isCapableOfLosslessOrHighBitrate = true,
            )
        }

        val dockDevice = devices.firstOrNull { it.type in DOCK }
        if (dockDevice != null) {
            val name = dockDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Dock"
            return OutputRouteState(
                kind = OutputKind.DOCK,
                deviceName = name,
                routeClassification = "Audio Dock",
                isCapableOfLosslessOrHighBitrate = true,
            )
        }

        if (outputTypes.contains(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) || btTelemetry?.isConnected == true) {
            val name = btTelemetry?.deviceName ?: "Bluetooth Audio"
            val codec = btTelemetry?.codecName ?: "Unknown"

            val isHighBitrate = isHighBitrateLossyBluetooth(codec, btTelemetry?.sampleRateHz)
            val classification = when {
                !btTelemetry?.hasNamedCodec.orFalse() -> "Bluetooth (Codec not reported by OS)"
                isHighBitrate -> "High-bitrate lossy Bluetooth ($codec)"
                else -> "Standard lossy Bluetooth ($codec)"
            }

            return OutputRouteState(
                kind = OutputKind.BLUETOOTH,
                deviceName = name,
                routeClassification = classification,
                bluetoothTelemetry = btTelemetry,
                isCapableOfLosslessOrHighBitrate = isHighBitrate,
            )
        }

        val speakerDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speakerDevice != null) {
            return OutputRouteState(
                kind = OutputKind.SPEAKER,
                deviceName = speakerDevice.productName?.toString() ?: "Phone Speaker",
                routeClassification = "Internal Speaker",
                isCapableOfLosslessOrHighBitrate = false,
            )
        }

        return OutputRouteState(
            kind = OutputKind.UNKNOWN,
            deviceName = "Output unknown",
            routeClassification = "Output unknown",
            isCapableOfLosslessOrHighBitrate = false,
        )
    }

    private fun isHighBitrateLossyBluetooth(codecName: String, sampleRateHz: Int?): Boolean {
        val codec = codecName.uppercase(Locale.ROOT)
        if ("LDAC" in codec || "LHDC" in codec || ("APTX" in codec && "LOSSLESS" in codec)) return true
        if ("UHQ" in codec) return true
        val samsung = SAMSUNG_NAMES.any { it in codec }
        return samsung && sampleRateHz != null && sampleRateHz >= 88_200
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    companion object {
        private const val TAG = "OutputRouteTracker"

        private val SAMSUNG_NAMES = listOf("SSC", "SAMSUNG", "SCALABLE", "SEAMLESS")

        private val WIRED = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
        )
        private val USB = setOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
        )
        private val HDMI = setOf(
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            29, // TYPE_HDMI_EARC, API 31
        )
        private val DOCK = setOf(
            AudioDeviceInfo.TYPE_DOCK,
            31, // TYPE_DOCK_ANALOG, API 34
        )
    }
}
