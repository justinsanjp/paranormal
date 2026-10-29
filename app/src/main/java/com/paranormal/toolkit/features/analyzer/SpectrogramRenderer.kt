package com.paranormal.toolkit.features.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import com.paranormal.toolkit.features.analyzer.dsp.Spectrogram
import kotlin.math.roundToInt

/** Renders STFT magnitude-in-dB frames to a false-color heatmap bitmap. */
object SpectrogramRenderer {

    private const val DB_FLOOR = -80f
    private const val DB_CEIL = 0f

    private val colorStops = intArrayOf(
        Color.rgb(10, 10, 20),
        Color.rgb(30, 60, 140),
        Color.rgb(20, 160, 130),
        Color.rgb(250, 200, 40),
        Color.rgb(255, 255, 255),
    )

    fun render(spectrogram: Spectrogram): Bitmap {
        val width = spectrogram.frames.size.coerceAtLeast(1)
        val height = spectrogram.binCount.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (x in 0 until width) {
            val bins = spectrogram.frames[x]
            for (y in 0 until height) {
                val bin = bins[y]
                val normalized = ((bin - DB_FLOOR) / (DB_CEIL - DB_FLOOR)).coerceIn(0f, 1f)
                val row = height - 1 - y // DC at the bottom, highest frequency at the top
                pixels[row * width + x] = colorFor(normalized)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun colorFor(t: Float): Int {
        val scaled = (t * (colorStops.size - 1)).coerceIn(0f, (colorStops.size - 1).toFloat())
        val lowIndex = scaled.toInt().coerceIn(0, colorStops.size - 2)
        val fraction = scaled - lowIndex
        return lerpColor(colorStops[lowIndex], colorStops[lowIndex + 1], fraction)
    }

    private fun lerpColor(from: Int, to: Int, fraction: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).roundToInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).roundToInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).roundToInt()
        return Color.rgb(r, g, b)
    }
}
