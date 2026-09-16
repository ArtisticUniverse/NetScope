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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.ar.core.TrackingState
import com.netscope.app.AppViewModel
import io.github.sceneview.ar.ARSceneView

private data class HeatPoint(val x: Float, val y: Float, val z: Float, val rssi: Int)
private data class ScreenPt(val x: Float, val y: Float, val rssi: Int)

@Composable
fun ArHeatmapScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val points = remember { mutableStateListOf<HeatPoint>() }
    var screenPts by remember { mutableStateOf<List<ScreenPt>>(emptyList()) }
    var tracking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notConnected by remember { mutableStateOf(false) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var lastPos by remember { mutableStateOf<FloatArray?>(null) }

    val viewM = remember { FloatArray(16) }
    val projM = remember { FloatArray(16) }

    Box(modifier.fillMaxSize().onSizeChanged { sizePx = it }) {

        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            onSessionFailed = { e -> failure = e.message ?: "AR session failed" },
            onSessionUpdated = { _, frame ->
                val cam = frame.camera
                tracking = cam.trackingState == TrackingState.TRACKING
                if (cam.trackingState == TrackingState.TRACKING) {
                    val pose = cam.pose
                    val px = pose.tx(); val py = pose.ty(); val pz = pose.tz()
                    val last = lastPos
                    val moved = last == null || run {
                        val dx = px - last[0]; val dy = py - last[1]; val dz = pz - last[2]
                        dx * dx + dy * dy + dz * dz > 0.35f * 0.35f
                    }
                    if (moved) {
                        val s = vm.sampleRssi()
                        if (s.connected) {
                            points.add(HeatPoint(px, py, pz, s.rssi))
                            if (points.size > 400) points.removeAt(0)
                            lastPos = floatArrayOf(px, py, pz)
                            notConnected = false
                        } else {
                            notConnected = true
                        }
                    }
                    cam.getViewMatrix(viewM, 0)
                    cam.getProjectionMatrix(projM, 0, 0.05f, 30f)
                    val w = sizePx.width.toFloat(); val h = sizePx.height.toFloat()
                    if (w > 0f && h > 0f) {
                        screenPts = points.mapNotNull { project(it, viewM, projM, w, h) }
                    }
                }
            },
        )

        // Heatmap overlay.
        Canvas(Modifier.fillMaxSize()) {
            screenPts.forEach { p ->
                drawCircle(color = colorFor(p.rssi).copy(alpha = 0.55f), radius = 36f,
                    center = Offset(p.x, p.y))
                drawCircle(color = colorFor(p.rssi), radius = 10f, center = Offset(p.x, p.y))
            }
        }

        // Top status + suggestion.
        TopPanel(
            points = points,
            tracking = tracking,
            notConnected = notConnected,
            failure = failure,
            onClear = { points.clear(); screenPts = emptyList(); lastPos = null },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Legend(Modifier.align(Alignment.BottomStart).padding(16.dp))
    }
}

@Composable
private fun TopPanel(
    points: SnapshotStateList<HeatPoint>,
    tracking: Boolean,
    notConnected: Boolean,
    failure: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weakest = points.minByOrNull { it.rssi }
    val strongest = points.maxByOrNull { it.rssi }

    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                failure != null -> Text(
                    "AR unavailable: $failure\nNeeds an ARCore-supported device with " +
                        "\"Google Play Services for AR\" installed.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                !tracking -> Text("Move the phone slowly to start AR tracking…",
                    fontWeight = FontWeight.SemiBold)
                notConnected -> Text("Connect the phone to Wi-Fi to map signal.",
                    color = MaterialTheme.colorScheme.error)
                else -> {
                    Text("Walk around your home — patches paint the Wi-Fi signal.",
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Samples: ${points.size}", style = MaterialTheme.typography.bodySmall)
                    if (weakest != null && strongest != null && points.size >= 4) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dead zone: ${weakest.rssi} dBm · Best: ${strongest.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "💡 Suggestion: the red patches are your weakest coverage. " +
                                "Move the router toward that area, raise it, or clear " +
                                "obstructions between it and there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weak", style = MaterialTheme.typography.labelSmall)
            Canvas(Modifier.height(10.dp).fillMaxWidth(0.4f)) {
                val steps = 20
                val w = size.width / steps
                for (i in 0 until steps) {
                    drawCircle(
                        color = colorFor((-85 + (i * 40 / steps))),
                        radius = size.height / 2f,
                        center = Offset(w * i + w / 2, size.height / 2f),
                    )
                }
            }
            Text("Strong", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** RSSI (-85 weak .. -45 strong) → red→green. */
private fun colorFor(rssi: Int): Color {
    val t = ((rssi + 85f) / 40f).coerceIn(0f, 1f)
    return Color(red = 1f - t, green = t, blue = 0.1f, alpha = 1f)
}

/** Project a world point to screen pixels using column-major AR matrices. */
private fun project(p: HeatPoint, view: FloatArray, proj: FloatArray, w: Float, h: Float): ScreenPt? {
    val cx = view[0] * p.x + view[4] * p.y + view[8] * p.z + view[12]
    val cy = view[1] * p.x + view[5] * p.y + view[9] * p.z + view[13]
    val cz = view[2] * p.x + view[6] * p.y + view[10] * p.z + view[14]
    val cw = view[3] * p.x + view[7] * p.y + view[11] * p.z + view[15]

    val clipX = proj[0] * cx + proj[4] * cy + proj[8] * cz + proj[12] * cw
    val clipY = proj[1] * cx + proj[5] * cy + proj[9] * cz + proj[13] * cw
    val clipW = proj[3] * cx + proj[7] * cy + proj[11] * cz + proj[15] * cw

    if (clipW <= 0f) return null
    val ndcX = clipX / clipW
    val ndcY = clipY / clipW
    if (ndcX < -1.2f || ndcX > 1.2f || ndcY < -1.2f || ndcY > 1.2f) return null

    val sx = (ndcX * 0.5f + 0.5f) * w
    val sy = (1f - (ndcY * 0.5f + 0.5f)) * h
    return ScreenPt(sx, sy, p.rssi)
}
