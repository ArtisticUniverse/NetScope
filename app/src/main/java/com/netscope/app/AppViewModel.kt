package com.netscope.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netscope.app.audio.MultiAudioManager
import com.netscope.app.bt.BluetoothScanner
import com.netscope.app.model.AudioSink
import com.netscope.app.model.BtDevice
import com.netscope.app.model.NetDevice
import com.netscope.app.motion.MotionDetector
import com.netscope.app.motion.WifiRssiSampler
import com.netscope.app.net.NetworkScanner
import com.netscope.app.net.NsdDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MdnsService(val name: String, val kind: String, val host: String?, val port: Int)

data class MotionUi(
    val connected: Boolean = false,
    val ssid: String? = null,
    val rssi: Int = -127,
    val std: Double = 0.0,
    val baseline: Double = 0.0,
    val moving: Boolean = false,
    val intensity: Float = 0f,
    val history: List<Int> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val netScanner = NetworkScanner(app)
    private val nsd = NsdDiscovery(app)
    private val btScanner = BluetoothScanner(app)
    val audioManager = MultiAudioManager(app)
    private val rssiSampler = WifiRssiSampler(app)
    private val motionDetector = MotionDetector()

    // ---- consent ----
    private val _consent = MutableStateFlow(false)
    val consent: StateFlow<Boolean> = _consent.asStateFlow()
    fun grantConsent() { _consent.value = true }

    // ---- network ----
    private val _localInfo = MutableStateFlow<NetworkScanner.LocalInfo?>(null)
    val localInfo: StateFlow<NetworkScanner.LocalInfo?> = _localInfo.asStateFlow()

    private val _netDevices = MutableStateFlow<List<NetDevice>>(emptyList())
    val netDevices: StateFlow<List<NetDevice>> = _netDevices.asStateFlow()

    private val _netProgress = MutableStateFlow(0f)
    val netProgress: StateFlow<Float> = _netProgress.asStateFlow()

    private val _netScanning = MutableStateFlow(false)
    val netScanning: StateFlow<Boolean> = _netScanning.asStateFlow()

    private val _mdns = MutableStateFlow<List<MdnsService>>(emptyList())
    val mdns: StateFlow<List<MdnsService>> = _mdns.asStateFlow()

    private var netJob: Job? = null

    fun refreshLocalInfo() {
        _localInfo.value = netScanner.localInfo()
    }

    fun startNetworkScan() {
        if (_netScanning.value) return
        _netScanning.value = true
        _netProgress.value = 0f
        _netDevices.value = emptyList()
        _mdns.value = emptyList()
        refreshLocalInfo()

        nsd.stop()
        nsd.start { name, kind, host, port ->
            val svc = MdnsService(name, kind, host, port)
            _mdns.update { (it + svc).distinctBy { s -> s.name + s.kind } }
        }

        netJob = viewModelScope.launch {
            netScanner.scan(
                onProgress = { p -> _netProgress.value = p },
                onDevice = { d ->
                    _netDevices.update { cur ->
                        (cur.filter { it.ip != d.ip } + d).sortedBy { ipKey(it.ip) }
                    }
                },
            )
            _netScanning.value = false
        }
    }

    fun stopNetworkScan() {
        netJob?.cancel()
        _netScanning.value = false
        nsd.stop()
    }

    private fun ipKey(ip: String): Long =
        ip.split(".").fold(0L) { acc, s -> acc * 256 + (s.toLongOrNull() ?: 0) }

    // ---- bluetooth ----
    private val _btDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    val btDevices: StateFlow<List<BtDevice>> = _btDevices.asStateFlow()

    private val _btScanning = MutableStateFlow(false)
    val btScanning: StateFlow<Boolean> = _btScanning.asStateFlow()

    fun btReady(): Boolean = btScanner.isReady()

    fun startBtScan() {
        if (_btScanning.value) return
        _btScanning.value = true
        _btDevices.value = emptyList()
        btScanner.start { d ->
            _btDevices.update { cur ->
                (cur.filter { it.address != d.address } + d)
                    .sortedWith(compareByDescending<BtDevice> { it.isAudioSink }
                        .thenByDescending { it.rssi ?: -999 })
            }
        }
    }

    fun stopBtScan() {
        btScanner.stop()
        _btScanning.value = false
    }

    // ---- audio ----
    private val _sinks = MutableStateFlow<List<AudioSink>>(emptyList())
    val sinks: StateFlow<List<AudioSink>> = _sinks.asStateFlow()

    fun refreshSinks() {
        _sinks.value = audioManager.outputs()
    }

    fun capabilities() = audioManager.capabilities()

    private val _nowPlaying = MutableStateFlow<String?>(null)
    val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()

    private val _audioError = MutableStateFlow<String?>(null)
    val audioError: StateFlow<String?> = _audioError.asStateFlow()

    fun play(uri: Uri, title: String) {
        _audioError.value = null
        audioManager.play(uri) { _audioError.value = it }
        _nowPlaying.value = title
    }

    fun stopAudio() {
        audioManager.stop()
        _nowPlaying.value = null
    }

    // ---- wi-fi motion sensing ----
    private val _motion = MutableStateFlow(MotionUi())
    val motion: StateFlow<MotionUi> = _motion.asStateFlow()

    private var motionJob: Job? = null
    private val maxHistory = 120

    fun startMotion() {
        if (motionJob?.isActive == true) return
        motionDetector.reset()
        motionJob = viewModelScope.launch {
            while (isActive) {
                val s = rssiSampler.read()
                if (s.connected) {
                    val r = motionDetector.add(s.rssi)
                    _motion.update { prev ->
                        val hist = (prev.history + s.rssi).takeLast(maxHistory)
                        MotionUi(
                            connected = true,
                            ssid = s.ssid,
                            rssi = s.rssi,
                            std = r.std,
                            baseline = r.baseline,
                            moving = r.moving,
                            intensity = r.intensity,
                            history = hist,
                        )
                    }
                } else {
                    _motion.update { it.copy(connected = false, ssid = null) }
                }
                delay(250)
            }
        }
    }

    fun stopMotion() {
        motionJob?.cancel()
        motionJob = null
    }

    fun calibrateMotion() = motionDetector.calibrate()

    // ---- AR heatmap sampling ----
    /** One-shot RSSI read for the AR heatmap (sampled per camera movement). */
    fun sampleRssi(): WifiRssiSampler.Sample = rssiSampler.read()

    override fun onCleared() {
        stopNetworkScan()
        stopBtScan()
        stopMotion()
        audioManager.stop()
        super.onCleared()
    }
}
