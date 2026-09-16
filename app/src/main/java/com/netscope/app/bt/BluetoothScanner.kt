package com.netscope.app.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.netscope.app.model.BtDevice
import com.netscope.app.model.BtKind

/**
 * Bluetooth discovery: Classic inquiry (ACTION_FOUND broadcasts) + BLE scan.
 * Only surfaces what devices publicly advertise: name, address, class, RSSI,
 * advertised service UUIDs. No pairing, connecting, or private data access here.
 */
@SuppressLint("MissingPermission") // callers gate on Perms.bluetoothPerms()
class BluetoothScanner(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? = manager?.adapter

    private var receiver: BroadcastReceiver? = null
    private var leCallback: ScanCallback? = null
    private var sink: ((BtDevice) -> Unit)? = null

    fun isReady(): Boolean = adapter?.isEnabled == true

    fun bondedAudioDevices(): List<BtDevice> = try {
        (adapter?.bondedDevices ?: emptySet()).map { it.toModel(rssi = null, bonded = true) }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun start(onDevice: (BtDevice) -> Unit) {
        val a = adapter ?: return
        sink = onDevice

        // Emit already-bonded devices immediately.
        bondedAudioDevices().forEach(onDevice)

        // Classic inquiry.
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    val dev: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                    dev?.let { sink?.invoke(it.toModel(rssi = rssi, bonded = it.isBondedSafe())) }
                }
            }
        }
        // API 34+ requires an explicit export flag for context-registered receivers.
        ContextCompat.registerReceiver(
            appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        } catch (_: SecurityException) {
        }

        // BLE scan.
        leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device ?: return
                val services = result.scanRecord?.serviceUuids
                    ?.map { it.uuid.toString() } ?: emptyList()
                sink?.invoke(
                    d.toModel(
                        rssi = result.rssi,
                        bonded = d.isBondedSafe(),
                        forceKind = BtKind.LE,
                        services = services,
                    )
                )
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            a.bluetoothLeScanner?.startScan(null, settings, leCallback)
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        val a = adapter
        try {
            a?.cancelDiscovery()
            leCallback?.let { a?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: SecurityException) {
        }
        receiver?.let {
            try {
                appContext.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        receiver = null
        leCallback = null
        sink = null
    }

    private fun BluetoothDevice.isBondedSafe(): Boolean = try {
        bondState == BluetoothDevice.BOND_BONDED
    } catch (_: SecurityException) {
        false
    }

    private fun BluetoothDevice.toModel(
        rssi: Int?,
        bonded: Boolean,
        forceKind: BtKind? = null,
        services: List<String> = emptyList(),
    ): BtDevice {
        val nm = try {
            name
        } catch (_: SecurityException) {
            null
        }
        val kind = forceKind ?: try {
            when (type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> BtKind.CLASSIC
                BluetoothDevice.DEVICE_TYPE_LE -> BtKind.LE
                BluetoothDevice.DEVICE_TYPE_DUAL -> BtKind.DUAL
                else -> BtKind.UNKNOWN
            }
        } catch (_: SecurityException) {
            BtKind.UNKNOWN
        }
        val cls = try {
            bluetoothClass
        } catch (_: SecurityException) {
            null
        }
        val isAudio = cls?.let {
            it.hasService(BluetoothClass.Service.AUDIO) ||
                it.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        } ?: false

        return BtDevice(
            address = address,
            name = nm,
            kind = kind,
            bonded = bonded,
            rssi = rssi,
            majorClass = cls?.majorLabel(),
            deviceClass = cls?.let { "0x%06X".format(it.deviceClass) },
            advertisedServices = services,
            isAudioSink = isAudio,
        )
    }

    private fun BluetoothClass.majorLabel(): String = when (majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.NETWORKING -> "Networking"
        else -> "Uncategorized"
    }
}
