package com.netscope.app.motion

import kotlin.math.sqrt

/**
 * Crude Wi-Fi "motion" sensing from RSSI variance.
 *
 * Honest note: this is NOT real CSI (Channel State Information) sensing — stock
 * Android does not expose CSI. When a person moves through the room, the multipath
 * of the Wi-Fi signal changes and the associated-AP RSSI wobbles. We measure the
 * rolling standard deviation of RSSI: a still room has a low, flat noise floor;
 * movement makes the variance jump. It is coarse and easily fooled (interference,
 * the phone moving, other radios), but it genuinely reacts to people walking.
 */
class MotionDetector(private val windowSize: Int = 24) {

    private val window = ArrayDeque<Int>()

    /** Learned noise floor (RSSI std-dev of a still room). Sensible default. */
    var baselineStd: Double = 1.2
        private set

    data class Reading(
        val rssi: Int,
        val std: Double,
        val baseline: Double,
        val moving: Boolean,
        /** 0f (calm) .. 1f+ (lots of movement), relative to baseline. */
        val intensity: Float,
    )

    fun reset() {
        window.clear()
    }

    fun add(rssi: Int): Reading {
        window.addLast(rssi)
        while (window.size > windowSize) window.removeFirst()

        val std = stdDev(window)
        // Movement when variance clearly exceeds the learned still-room floor.
        val threshold = maxOf(baselineStd * 1.8, baselineStd + 1.5)
        val moving = window.size >= windowSize / 2 && std > threshold
        val intensity = if (baselineStd > 0.0) {
            ((std - baselineStd) / (baselineStd * 3.0)).coerceIn(0.0, 1.0).toFloat()
        } else 0f

        return Reading(rssi, std, baselineStd, moving, intensity)
    }

    /** Snapshot the current variance as the "still room" baseline. */
    fun calibrate() {
        if (window.size >= 4) {
            baselineStd = maxOf(0.5, stdDev(window))
        }
    }

    private fun stdDev(values: Collection<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}
