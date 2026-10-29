package com.paranormal.toolkit.features.analyzer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val sourceSampleRateHz: Int,
    val decimationFactor: Int,
) {
    val isDownsampled: Boolean get() = decimationFactor > 1
}

/** Reads a mono 16-bit PCM WAV file (the format [WavFileWriter] produces) into -1f..1f samples. */
object WavDecoder {

    /**
     * Sample budget for one clip. An imported file can be arbitrarily long,
     * and the Analyzer holds an original plus a working copy plus the
     * spectrogram, so an unbounded read would exhaust the heap. Over budget
     * the clip is averaged down by an integer factor and the reported sample
     * rate is divided by the same factor - duration and pitch stay correct,
     * only high frequencies are lost.
     */
    const val MAX_SAMPLES = 8_000_000L

    private const val HEADER_BYTES = 44
    private const val CHUNK_BYTES = 1 shl 16 // even, so 16-bit frames never split

    fun decode(file: File): DecodedAudio {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(HEADER_BYTES)
            raf.readFully(header)
            val sourceRate = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(24)

            val totalSamples = ((raf.length() - HEADER_BYTES) / 2).coerceAtLeast(0)
            if (totalSamples == 0L) {
                return DecodedAudio(FloatArray(0), sourceRate, sourceRate, 1)
            }

            val factor = ((totalSamples + MAX_SAMPLES - 1) / MAX_SAMPLES).toInt().coerceAtLeast(1)
            val samples = FloatArray((totalSamples / factor).toInt())
            val buffer = ByteArray(CHUNK_BYTES)

            var outIndex = 0
            var accumulator = 0f
            var accumulated = 0
            var bytesLeft = totalSamples * 2

            while (bytesLeft > 0 && outIndex < samples.size) {
                val toRead = minOf(buffer.size.toLong(), bytesLeft).toInt()
                raf.readFully(buffer, 0, toRead)
                bytesLeft -= toRead

                var i = 0
                while (i + 1 < toRead && outIndex < samples.size) {
                    val low = buffer[i].toInt() and 0xFF
                    val high = buffer[i + 1].toInt() shl 8
                    accumulator += (high or low).toShort() / 32_768f
                    if (++accumulated == factor) {
                        samples[outIndex++] = accumulator / factor
                        accumulator = 0f
                        accumulated = 0
                    }
                    i += 2
                }
            }

            return DecodedAudio(
                samples = samples,
                sampleRateHz = (sourceRate / factor).coerceAtLeast(1),
                sourceSampleRateHz = sourceRate,
                decimationFactor = factor,
            )
        }
    }
}
