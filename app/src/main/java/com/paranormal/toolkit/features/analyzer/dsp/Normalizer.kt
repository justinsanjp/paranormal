package com.paranormal.toolkit.features.analyzer.dsp

import kotlin.math.abs
import kotlin.math.sqrt

object Normalizer {

    fun peakNormalize(samples: FloatArray, targetPeak: Float = 0.95f): FloatArray {
        var peak = 0f
        for (s in samples) peak = maxOf(peak, abs(s))
        if (peak <= 0f) return samples.copyOf()
        val gain = targetPeak / peak
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }

    fun rmsNormalize(samples: FloatArray, targetRms: Float = 0.15f): FloatArray {
        if (samples.isEmpty()) return samples.copyOf()
        var sumSquares = 0.0
        for (s in samples) sumSquares += s.toDouble() * s.toDouble()
        val rms = sqrt(sumSquares / samples.size).toFloat()
        if (rms <= 0f) return samples.copyOf()
        val gain = targetRms / rms
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }
}
