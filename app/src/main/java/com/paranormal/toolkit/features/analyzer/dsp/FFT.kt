package com.paranormal.toolkit.features.analyzer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley-Tukey FFT. [real]/[imag] length must be
 * a power of two.
 */
object FFT {
    fun transform(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be a power of two, was $n" }
        if (n <= 1) return

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wReal = cos(angle)
            val wImag = sin(angle)
            var start = 0
            while (start < n) {
                var curReal = 1.0
                var curImag = 0.0
                for (k in 0 until length / 2) {
                    val evenIdx = start + k
                    val oddIdx = start + k + length / 2
                    val oddReal = real[oddIdx] * curReal - imag[oddIdx] * curImag
                    val oddImag = real[oddIdx] * curImag + imag[oddIdx] * curReal
                    real[oddIdx] = real[evenIdx] - oddReal
                    imag[oddIdx] = imag[evenIdx] - oddImag
                    real[evenIdx] += oddReal
                    imag[evenIdx] += oddImag
                    val nextReal = curReal * wReal - curImag * wImag
                    val nextImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    curImag = nextImag
                }
                start += length
            }
            length = length shl 1
        }
    }
}
