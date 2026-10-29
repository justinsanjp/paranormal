package com.paranormal.toolkit.features.analyzer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Magnitude-in-dB STFT frames: `frames[frameIndex][bin]`, bin 0 = DC. */
class Spectrogram(
    val frames: Array<FloatArray>,
    val fftSize: Int,
    val hopSize: Int,
    val sampleRateHz: Int,
) {
    val binCount get() = fftSize / 2
    fun frequencyForBin(bin: Int): Float = bin * sampleRateHz.toFloat() / fftSize
}

object SpectrogramComputer {

    fun compute(
        samples: FloatArray,
        sampleRateHz: Int,
        fftSize: Int = 1024,
        hopSize: Int = fftSize / 4,
    ): Spectrogram {
        require(fftSize and (fftSize - 1) == 0) { "fftSize must be a power of two" }
        val window = hannWindow(fftSize)
        val frameCount = if (samples.size < fftSize) 1 else (samples.size - fftSize) / hopSize + 1
        val frames = Array(frameCount) { FloatArray(fftSize / 2) }

        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)

        for (frameIndex in 0 until frameCount) {
            val offset = frameIndex * hopSize
            for (i in 0 until fftSize) {
                val sampleIndex = offset + i
                val sample = if (sampleIndex < samples.size) samples[sampleIndex] else 0f
                real[i] = sample * window[i]
                imag[i] = 0.0
            }
            FFT.transform(real, imag)
            val bins = frames[frameIndex]
            for (bin in bins.indices) {
                val magnitude = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                bins[bin] = (20.0 * log10(max(magnitude, 1e-9))).toFloat()
            }
        }
        return Spectrogram(frames, fftSize, hopSize, sampleRateHz)
    }

    private fun hannWindow(size: Int): DoubleArray = DoubleArray(size) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))
    }
}
