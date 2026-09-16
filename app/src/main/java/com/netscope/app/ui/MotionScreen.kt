package com.netscope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netscope.app.AppViewModel

@Composable
fun MotionScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val m by vm.motion.collectAsState()

    // Sample only while this screen is visible.
    DisposableEffect(Unit) {
        vm.startMotion()
        onDispose { vm.stopMotion() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wi-Fi Motion Sensing", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)

        if (!m.connected) {
            Card { Text("Connect the phone to Wi-Fi to sense movement.", Modifier.padding(16.dp)) }
        }

        // Big status card.
        val moving = m.moving && m.connected
        Card(colors = CardDefaults.cardColors(
            containerColor = if (moving) Color(0xFFB3261E) else Color(0xFF1B7F4B)
        )) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (moving) "MOVEMENT DETECTED" else "ROOM IS STILL",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(m.ssid?.let { "on \"$it\"" } ?: "—", color = Color.White)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { m.intensity },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Live RSSI graph.
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Live RSSI (dBm)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                RssiGraph(
                    history = m.history,
                    lineColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }

        // Stats.
        Card {
            Column(Modifier.padding(16.dp)) {
                Stat("Current RSSI", "${m.rssi} dBm")
                Stat("Variance (std)", "%.2f".format(m.std))
                Stat("Still-room baseline", "%.2f".format(m.baseline))
                Stat("Motion intensity", "%.0f%%".format(m.intensity * 100))
            }
        }

        Row {
            OutlinedButton(onClick = { vm.calibrateMotion() }) { Text("Calibrate (stand still 5s)") }
        }

        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "How it works: this reads the wobble (variance) in your Wi-Fi signal. " +
                    "A person walking changes the radio multipath and the signal jitters. " +
                    "It's an approximation — not real CSI sensing — so interference or moving " +
                    "the phone can trigger it. Tap Calibrate while standing still to learn the " +
                    "room's noise floor, then walk around to see it react.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RssiGraph(history: List<Int>, lineColor: Color, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val minR = -95f
            val maxR = -30f
            val range = maxR - minR
            // gridlines
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (history.size < 2) return@Canvas
            val stepX = size.width / (history.size - 1)
            val path = Path()
            history.forEachIndexed { i, rssi ->
                val clamped = rssi.toFloat().coerceIn(minR, maxR)
                val yNorm = 1f - (clamped - minR) / range
                val x = stepX * i
                val y = yNorm * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
