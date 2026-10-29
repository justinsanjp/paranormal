package com.paranormal.toolkit.features.recorder

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams raw 16-bit PCM samples straight to disk instead of buffering a whole
 * session in memory, then patches the RIFF/WAV header sizes on [close].
 */
class WavFileWriter(
    file: File,
    private val sampleRateHz: Int,
    private val channels: Int,
    private val bitsPerSample: Int,
) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten = 0L
    private val sampleBytes = ByteArray(1 shl 16)

    init {
        raf.setLength(0)
        raf.write(ByteArray(44)) // placeholder header, patched in close()
    }

    fun write(buffer: ShortArray, length: Int) {
        val needed = length * 2
        val out = if (sampleBytes.size >= needed) sampleBytes else ByteArray(needed)
        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        raf.write(out, 0, needed)
        dataBytesWritten += needed
    }

    fun close() {
        patchHeader()
        raf.close()
    }

    private fun patchHeader() {
        val byteRate = sampleRateHz * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36 + dataBytesWritten).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRateHz)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytesWritten.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}
