package com.paranormal.toolkit.features.ghostbox

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Quadrature FM discriminator: turns interleaved unsigned 8-bit I/Q samples
 * (the RTL2832U's native output format) into demodulated audio, with a
 * de-emphasis low-pass matching broadcast FM's pre-emphasis curve.
 */
class FmDemodulator(sampleRateHz: Int, deEmphasisMicros: Float = 50f) {

    private var lastI = 0f
    private var lastQ = 0f
    private var deEmphasisState = 0f
    private val deEmphasisAlpha = 1f - exp(-1f / (sampleRateHz * deEmphasisMicros * 1e-6f))

    /** [iqBytes] are unsigned 8-bit I,Q,I,Q,... samples straight off the USB bulk endpoint. */
    fun demodulate(iqBytes: ByteArray, length: Int): FloatArray {
        val sampleCount = length / 2
        val out = FloatArray(sampleCount)
        for (n in 0 until sampleCount) {
            val i = ((iqBytes[n * 2].toInt() and 0xFF) - 128) / 128f
            val q = ((iqBytes[n * 2 + 1].toInt() and 0xFF) - 128) / 128f

            // Instantaneous frequency = phase delta between consecutive I/Q samples
            // (conjugate multiply, then take the angle).
            val real = i * lastI + q * lastQ
            val imag = q * lastI - i * lastQ
            val demod = atan2(imag, real)
            lastI = i
            lastQ = q

            deEmphasisState += deEmphasisAlpha * (demod - deEmphasisState)
            out[n] = deEmphasisState
        }
        return out
    }
}

/**
 * Anti-aliasing low-pass followed by an integer downsample, to get from the
 * dongle's IQ rate down to the audio rate.
 *
 * Decimating on a box-car average alone folds everything above the output
 * Nyquist back into the audio band as hiss, so two cascaded biquads roll it
 * off first, cut off at 80% of the output Nyquist. Filter state and the
 * partially filled output sample are kept between calls, so block
 * boundaries in the USB read loop don't click.
 */
class Decimator(factor: Int) {

    private val factor = factor.coerceAtLeast(1)
    private val stages: List<Biquad> =
        if (this.factor > 1) List(2) { Biquad(CUTOFF_FRACTION / this.factor) } else emptyList()

    private var accumulator = 0f
    private var accumulated = 0

    fun decimate(samples: FloatArray, length: Int = samples.size): FloatArray {
        if (factor == 1) return if (length == samples.size) samples else samples.copyOf(length)

        val out = FloatArray(length / factor + 1)
        var outIndex = 0
        for (i in 0 until length) {
            var value = samples[i]
            for (stage in stages) value = stage.process(value)
            accumulator += value
            if (++accumulated == factor) {
                if (outIndex < out.size) out[outIndex++] = accumulator / factor
                accumulator = 0f
                accumulated = 0
            }
        }
        return if (outIndex == out.size) out else out.copyOf(outIndex)
    }

    /** RBJ cookbook low-pass, cutoff given as a fraction of the input sample rate. */
    private class Biquad(cutoffFraction: Double, q: Double = 0.707) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val omega = 2.0 * PI * cutoffFraction.coerceIn(1e-5, 0.49)
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            val rawB1 = 1.0 - cosOmega
            val rawB0 = rawB1 / 2.0
            val a0 = 1.0 + alpha
            b0 = rawB0 / a0
            b1 = rawB1 / a0
            b2 = rawB0 / a0
            a1 = (-2.0 * cosOmega) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(sample: Float): Float {
            val x0 = sample.toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0.toFloat()
        }
    }

    private companion object {
        /** Cutoff at 80% of the output Nyquist, expressed against the input rate. */
        const val CUTOFF_FRACTION = 0.4
    }
}
