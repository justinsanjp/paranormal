package com.paranormal.toolkit.features.analyzer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class FilterType { LOW_PASS, HIGH_PASS, BAND_PASS }

/**
 * Standard RBJ "Audio EQ Cookbook" biquad (Direct Form I), applied as a
 * single forward pass over the whole clip.
 */
class BiquadFilter(type: FilterType, cutoffHz: Float, sampleRateHz: Int, q: Float = 0.707f) {

    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    init {
        val nyquist = sampleRateHz / 2f
        val safeCutoff = cutoffHz.coerceIn(20f, nyquist - 20f)
        val omega = 2.0 * PI * safeCutoff / sampleRateHz
        val alpha = sin(omega) / (2.0 * q)
        val cosOmega = cos(omega)

        val rawB0: Double
        val rawB1: Double
        val rawB2: Double
        val rawA0: Double
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha

        when (type) {
            FilterType.LOW_PASS -> {
                rawB1 = 1.0 - cosOmega
                rawB0 = rawB1 / 2.0
                rawB2 = rawB0
                rawA0 = 1.0 + alpha
            }
            FilterType.HIGH_PASS -> {
                rawB0 = (1.0 + cosOmega) / 2.0
                rawB1 = -(1.0 + cosOmega)
                rawB2 = rawB0
                rawA0 = 1.0 + alpha
            }
            FilterType.BAND_PASS -> {
                rawB0 = alpha
                rawB1 = 0.0
                rawB2 = -alpha
                rawA0 = 1.0 + alpha
            }
        }

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun apply(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            output[i] = y0.toFloat()
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return output
    }
}
