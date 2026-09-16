package com.netscope.app.model

/** A device discovered on the local IP network. */
data class NetDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val openPorts: List<PortInfo> = emptyList(),
    val services: List<String> = emptyList(),
)

data class PortInfo(val port: Int, val service: String)

enum class BtKind { CLASSIC, LE, DUAL, UNKNOWN }

/** A discovered Bluetooth device (Classic or BLE). */
data class BtDevice(
    val address: String,
    val name: String?,
    val kind: BtKind,
    val bonded: Boolean,
    val rssi: Int? = null,
    val majorClass: String? = null,
    val deviceClass: String? = null,
    val advertisedServices: List<String> = emptyList(),
    val isAudioSink: Boolean = false,
)

/** An audio output the phone can currently route to. */
data class AudioSink(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isBluetooth: Boolean,
    val supportsLeAudio: Boolean = false,
)
