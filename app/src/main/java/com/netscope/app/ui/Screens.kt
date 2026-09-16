package com.netscope.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netscope.app.AppViewModel
import com.netscope.app.model.AudioSink
import com.netscope.app.model.BtDevice
import com.netscope.app.model.BtKind
import com.netscope.app.model.NetDevice
import com.netscope.app.util.Perms

/* ------------------------- Consent gate ------------------------- */

@Composable
fun ConsentScreen(onAgree: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("NetScope", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A local-network and Bluetooth visibility tool.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Ethical use", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "NetScope only surfaces what devices publicly reveal on a network you " +
                        "are authorised to inspect: addresses, vendor, advertised services, " +
                        "and open well-known ports. It does NOT capture traffic, break into, " +
                        "or read private data from any device.\n\n" +
                        "Only scan networks you own or have explicit permission to test. " +
                        "Unauthorised scanning may be illegal in your jurisdiction.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
            Text("I am authorised to scan this network")
        }
    }
}

/* ------------------------- Network ------------------------- */

@Composable
fun NetworkScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val info by vm.localInfo.collectAsState()
    val devices by vm.netDevices.collectAsState()
    val scanning by vm.netScanning.collectAsState()
    val progress by vm.netProgress.collectAsState()
    val mdns by vm.mdns.collectAsState()

    LaunchedEffect(Unit) { vm.refreshLocalInfo() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("This device", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    KeyVal("Wi-Fi", info?.ssid ?: "—")
                    KeyVal("Local IP", info?.localIp ?: "—")
                    KeyVal("Gateway", info?.gatewayIp ?: "—")
                    KeyVal("Subnet", info?.let { "/${it.prefixLength}" } ?: "—")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (scanning) vm.stopNetworkScan() else vm.startNetworkScan() },
                ) { Text(if (scanning) "Stop" else "Scan network") }
                Spacer(Modifier.width(12.dp))
                Text("${devices.size} device(s)", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (scanning) {
            item { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
        }
        items(devices, key = { it.ip }) { d -> NetDeviceCard(d) }

        if (mdns.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Advertised services (mDNS)", fontWeight = FontWeight.Bold)
            }
            items(mdns, key = { it.name + it.kind }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${s.kind}${s.host?.let { " · $it:${s.port}" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetDeviceCard(d: NetDevice) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (d.isGateway) Icons.Filled.Router else Icons.Filled.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(d.ip, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                if (d.isSelf) Badge("this device")
                if (d.isGateway) Badge("gateway")
            }
            d.hostname?.let { KeyVal("Host", it) }
            KeyVal("MAC", d.mac ?: "hidden by OS")
            d.vendor?.let { KeyVal("Vendor", it) }
            if (d.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ChipRow(d.openPorts.map { "${it.port} ${it.service}" })
            }
        }
    }
}

/* ------------------------- Bluetooth ------------------------- */

@Composable
fun BluetoothScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val devices by vm.btDevices.collectAsState()
    val scanning by vm.btScanning.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) vm.startBtScan()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (scanning) {
                        vm.stopBtScan()
                    } else if (Perms.hasAll(context, Perms.bluetoothPerms())) {
                        vm.startBtScan()
                    } else {
                        launcher.launch(Perms.bluetoothPerms())
                    }
                }) { Text(if (scanning) "Stop" else "Scan Bluetooth") }
                Spacer(Modifier.width(12.dp))
                if (scanning) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
        if (!vm.btReady()) {
            item {
                Card { Text("Bluetooth is off or unavailable.", Modifier.padding(14.dp)) }
            }
        }
        item { Text("${devices.size} device(s)", style = MaterialTheme.typography.bodyMedium) }
        items(devices, key = { it.address }) { d -> BtDeviceCard(d) }
    }
}

@Composable
private fun BtDeviceCard(d: BtDevice) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.name ?: "(unnamed)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                if (d.isAudioSink) Badge("audio")
                if (d.bonded) Badge("paired")
            }
            KeyVal("Address", d.address)
            KeyVal("Type", d.kind.name + (d.majorClass?.let { " · $it" } ?: ""))
            d.rssi?.let { KeyVal("Signal", "$it dBm") }
            if (d.advertisedServices.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ChipRow(d.advertisedServices.map { it.take(8) })
            }
        }
    }
}

/* ------------------------- Multi-Audio ------------------------- */

@Composable
fun AudioScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sinks by vm.sinks.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val error by vm.audioError.collectAsState()
    val caps = remember { vm.capabilities() }
    val selected = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(Unit) { vm.refreshSinks() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermissionSafely(uri)
            vm.play(uri, uri.lastPathSegment ?: "track")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )) {
                Column(Modifier.padding(16.dp)) {
                    Text("Can this phone play to several speakers at once?",
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(caps.verdict, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(caps.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row {
                Button(onClick = { vm.refreshSinks() }) { Text("Refresh outputs") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    context.startActivity(vm.audioManager.bluetoothSettingsIntent())
                }) { Text("Bluetooth settings") }
            }
        }
        item {
            Text("Output devices  ·  tick the speakers to play together",
                fontWeight = FontWeight.Bold)
        }
        items(sinks, key = { it.id }) { s ->
            AudioSinkRow(s, selected[s.id] == true) { checked -> selected[s.id] = checked }
        }
        item {
            val chosen = sinks.count { selected[it.id] == true }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { picker.launch("audio/*") }) { Text("Pick track & play") }
                Spacer(Modifier.width(12.dp))
                if (nowPlaying != null) {
                    OutlinedButton(onClick = { vm.stopAudio() }) { Text("Stop") }
                }
            }
            Spacer(Modifier.height(8.dp))
            val note = when {
                chosen <= 1 -> "Audio plays on the system's active output."
                caps.leAudioBroadcastSource ->
                    "$chosen selected. With LE Audio Auracast, receivers that join the " +
                        "broadcast will play together."
                caps.leAudioUnicast ->
                    "$chosen selected. Simultaneous output works only with LE-Audio speakers."
                else ->
                    "$chosen selected, but classic Bluetooth plays to one speaker only. " +
                        "Use your phone's Dual Audio toggle in Bluetooth settings to drive two."
            }
            Text(note, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            nowPlaying?.let {
                Spacer(Modifier.height(8.dp))
                Text("Now playing: $it", fontWeight = FontWeight.SemiBold)
            }
            error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AudioSinkRow(s: AudioSink, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheck, enabled = s.isBluetooth)
            Column(Modifier.padding(vertical = 12.dp).weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                Text(
                    s.typeLabel + if (s.supportsLeAudio) " · LE Audio" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (s.isBluetooth) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* ------------------------- shared bits ------------------------- */

@Composable
private fun KeyVal(k: String, v: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text("$k: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Badge(text: String) {
    AssistChip(onClick = {}, label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.padding(end = 4.dp))
}

@Composable
private fun ChipRow(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
        }
    }
}

private fun android.content.ContentResolver.takePersistableUriPermissionSafely(
    uri: android.net.Uri
) {
    // GetContent grants transient read access; nothing to persist. No-op kept for clarity.
}
