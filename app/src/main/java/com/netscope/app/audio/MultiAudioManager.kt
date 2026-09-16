package com.netscope.app.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.netscope.app.model.AudioSink

/**
 * Manages audio output enumeration and local playback, and — crucially — reports
 * honestly what "play on multiple speakers at once" actually means on this device.
 *
 * Public Android SDK routes A2DP audio to ONE sink. Genuine multi-speaker output
 * requires either a vendor feature (e.g. Samsung Dual Audio, system-controlled) or
 * Bluetooth LE Audio / Auracast broadcast (Android 13+/14+, all receivers must
 * support LE Audio). This class detects those and guides the user accordingly
 * rather than faking simultaneous playback.
 */
@SuppressLint("MissingPermission")
class MultiAudioManager(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var player: MediaPlayer? = null

    data class Capabilities(
        val leAudioUnicast: Boolean,
        val leAudioBroadcastSource: Boolean,
        val android13Plus: Boolean,
        val summary: String,
        val verdict: String,
    )

    fun capabilities(): Capabilities {
        val api = Build.VERSION.SDK_INT
        var unicast = false
        var broadcast = false

        if (api >= Build.VERSION_CODES.TIRAMISU && adapter != null) {
            unicast = try {
                adapter.isLeAudioSupported == BluetoothStatusCodes.FEATURE_SUPPORTED
            } catch (_: Throwable) {
                false
            }
        }
        if (api >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && adapter != null) {
            broadcast = try {
                adapter.isLeAudioBroadcastSourceSupported == BluetoothStatusCodes.FEATURE_SUPPORTED
            } catch (_: Throwable) {
                false
            }
        }

        val verdict = when {
            broadcast ->
                "This phone supports LE Audio Auracast broadcast — it can stream one " +
                    "audio source to many LE-Audio receivers at once. Every speaker must " +
                    "also support LE Audio."
            unicast ->
                "This phone supports LE Audio. Simultaneous multi-speaker playback is " +
                    "possible only with LE-Audio receivers, and broadcast is not available " +
                    "on this Android version."
            else ->
                "This phone exposes only classic Bluetooth audio to apps, which routes to " +
                    "ONE speaker at a time. True multi-speaker playback here depends on a " +
                    "vendor feature such as Samsung Dual Audio, set in system Bluetooth " +
                    "settings — no app can force it via the public SDK."
        }

        val summary = buildString {
            append("Android API $api. ")
            append("LE Audio: ${if (unicast) "yes" else "no"}. ")
            append("Auracast broadcast: ${if (broadcast) "yes" else "no"}.")
        }

        return Capabilities(unicast, broadcast, api >= 33, summary, verdict)
    }

    /** Audio outputs the system can currently route media to. */
    fun outputs(): List<AudioSink> {
        val caps = capabilities()
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { it.toSink(caps.leAudioUnicast) }
            .filter { it.typeLabel != "Telephony" }
    }

    fun bluetoothOutputs(): List<AudioSink> = outputs().filter { it.isBluetooth }

    private fun AudioDeviceInfo.toSink(leCap: Boolean): AudioSink {
        val (label, bt, le) = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Triple("Bluetooth A2DP", true, false)
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Triple("Bluetooth SCO", true, false)
            AudioDeviceInfo.TYPE_BLE_HEADSET -> Triple("LE Audio headset", true, true)
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> Triple("LE Audio speaker", true, true)
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> Triple("LE Audio broadcast", true, true)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Triple("Phone speaker", false, false)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> Triple("Wired", false, false)
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> Triple("USB", false, false)
            AudioDeviceInfo.TYPE_TELEPHONY -> Triple("Telephony", false, false)
            else -> Triple("Output", false, false)
        }
        val nm = try {
            productName?.toString()?.ifBlank { label } ?: label
        } catch (_: Throwable) {
            label
        }
        return AudioSink(
            id = id,
            name = nm,
            typeLabel = label,
            isBluetooth = bt,
            supportsLeAudio = le && leCap,
        )
    }

    /** Play a local audio Uri through the system's currently selected output(s). */
    fun play(uri: Uri, onError: (String) -> Unit) {
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, uri)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    onError("Playback error ($what/$extra)")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Could not play track")
        }
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun stop() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    /** Intent to the system Bluetooth settings, where Dual Audio / LE Audio live. */
    fun bluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
