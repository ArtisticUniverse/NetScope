package com.netscope.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netscope.app.ui.AudioScreen
import com.netscope.app.ui.BluetoothScreen
import com.netscope.app.ui.ArHeatmapScreen
import com.netscope.app.ui.ConsentScreen
import com.netscope.app.ui.MotionScreen
import com.netscope.app.ui.NetworkScreen
import com.netscope.app.ui.NetScopeTheme

enum class Tab(val label: String) {
    NETWORK("Network"), BLUETOOTH("Bluetooth"), AUDIO("Audio"), MOTION("Motion"), HEATMAP("AR")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetScopeTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val consent by vm.consent.collectAsState()
    if (!consent) {
        ConsentScreen(onAgree = { vm.grantConsent() })
        return
    }

    var tab by remember { mutableStateOf(Tab.NETWORK) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.NETWORK,
                    onClick = { tab = Tab.NETWORK },
                    icon = { Icon(Icons.Filled.Lan, null) },
                    label = { Text(Tab.NETWORK.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.BLUETOOTH,
                    onClick = { tab = Tab.BLUETOOTH },
                    icon = { Icon(Icons.Filled.Bluetooth, null) },
                    label = { Text(Tab.BLUETOOTH.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.AUDIO,
                    onClick = { tab = Tab.AUDIO },
                    icon = { Icon(Icons.Filled.LibraryMusic, null) },
                    label = { Text(Tab.AUDIO.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.MOTION,
                    onClick = { tab = Tab.MOTION },
                    icon = { Icon(Icons.Filled.Sensors, null) },
                    label = { Text(Tab.MOTION.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.HEATMAP,
                    onClick = { tab = Tab.HEATMAP },
                    icon = { Icon(Icons.Filled.ViewInAr, null) },
                    label = { Text(Tab.HEATMAP.label) },
                )
            }
        }
    ) { inner ->
        val m = Modifier.padding(inner)
        when (tab) {
            Tab.NETWORK -> NetworkScreen(vm, m)
            Tab.BLUETOOTH -> BluetoothScreen(vm, m)
            Tab.AUDIO -> AudioScreen(vm, m)
            Tab.MOTION -> MotionScreen(vm, m)
            Tab.HEATMAP -> ArHeatmapScreen(vm, m)
        }
    }
}
