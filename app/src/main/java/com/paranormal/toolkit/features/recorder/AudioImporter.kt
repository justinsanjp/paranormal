package com.paranormal.toolkit.features.recorder

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UnsupportedAudioException(message: String) : Exception(message)

data class ImportedAudio(val file: File, val sampleRateHz: Int, val sampleCount: Long)

/**
 * Imports an arbitrary audio file from the user's storage and converts it to
 * the mono 16-bit PCM WAV the rest of the toolkit works with, using the
 * platform's own decoders (MediaExtractor + MediaCodec). Whatever the device
 * can play - mp3, m4a/aac, ogg, flac, wav, amr - the Analyzer can then edit.
 *
 * Multi-channel input is downmixed by averaging the channels; the source
 * sample rate is kept as-is so nothing is resampled on the way in.
 */
object AudioImporter {

    fun importToWav(context: Context, uri: Uri, target: File): ImportedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw UnsupportedAudioException("Datei konnte nicht geöffnet werden: ${e.message}")
        }

        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw UnsupportedAudioException("Die Datei enthält keine Audiospur.")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw UnsupportedAudioException("Unbekanntes Audioformat.")

            return if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                copyRawPcm(extractor, format, target)
            } else {
                decodeWithCodec(extractor, format, mime, target)
            }
        } finally {
            extractor.release()
        }
    }

    /** Uncompressed input (e.g. a plain WAV) already arrives as PCM from the extractor. */
    private fun copyRawPcm(
        extractor: MediaExtractor,
        format: MediaFormat,
        target: File,
    ): ImportedAudio {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val encoding = format.pcmEncoding()

        val writer = WavFileWriter(target, sampleRate, channels = 1, bitsPerSample = 16)
        val input = ByteBuffer.allocate(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        var mono = ShortArray(0)
        var written = 0L
        try {
            while (true) {
                input.clear()
                val size = extractor.readSampleData(input, 0)
                if (size < 0) break
                mono = ensureCapacity(mono, frameCount(size, channels, encoding))
                val count = toMono(input, 0, size, channels, encoding, mono)
                writer.write(mono, count)
                written += count
                if (!extractor.advance()) break
            }
        } finally {
            writer.close()
        }

        if (written == 0L) throw UnsupportedAudioException("Die Audiospur enthält keine Daten.")
        return ImportedAudio(target, sampleRate, written)
    }

    private fun decodeWithCodec(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
        target: File,
    ): ImportedAudio {
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()
            ?: throw UnsupportedAudioException("Für $mime hat dieses Gerät keinen Decoder.")

        var writer: WavFileWriter? = null
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var mono = ShortArray(0)
        var written = 0L

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleRounds = 0

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            // On the last sample advance() fails; readSampleData then
                            // returns -1 next round and the EOS flag goes in.
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        encoding = out.pcmEncoding()
                        idleRounds = 0
                    }
                    outIndex >= 0 -> {
                        idleRounds = 0
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null) {
                                val sink = writer
                                    ?: WavFileWriter(target, sampleRate, 1, 16).also { writer = it }
                                mono = ensureCapacity(mono, frameCount(info.size, channels, encoding))
                                val count = toMono(buffer, info.offset, info.size, channels, encoding, mono)
                                sink.write(mono, count)
                                written += count
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    else -> {
                        // Guards against a malformed file that never reports end of stream.
                        if (++idleRounds > MAX_IDLE_ROUNDS) outputDone = true
                    }
                }
            }
        } catch (e: MediaCodec.CodecException) {
            throw UnsupportedAudioException("Decoder-Fehler: ${e.message}")
        } catch (e: IllegalStateException) {
            throw UnsupportedAudioException("Decoder-Fehler: ${e.message}")
        } finally {
            runCatching { codec.stop() }
            codec.release()
            writer?.close()
        }

        if (written == 0L) throw UnsupportedAudioException("Es konnten keine Audiodaten dekodiert werden.")
        return ImportedAudio(target, sampleRate, written)
    }

    private fun toMono(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        encoding: Int,
        out: ShortArray,
    ): Int {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = bytesPerSample(encoding)
        val frameBytes = bytesPerSample * channels
        if (frameBytes == 0) return 0
        val frames = minOf(size / frameBytes, out.size)
        for (frame in 0 until frames) {
            var sum = 0f
            val frameStart = offset + frame * frameBytes
            for (channel in 0 until channels) {
                sum += readSample(buffer, frameStart + channel * bytesPerSample, encoding)
            }
            val value = (sum / channels).coerceIn(-1f, 1f)
            out[frame] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return frames
    }

    private fun readSample(buffer: ByteBuffer, at: Int, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get(at).toInt() and 0xFF) - 128) / 128f
        AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat(at)
        ENCODING_PCM_24BIT_PACKED -> {
            val low = buffer.get(at).toInt() and 0xFF
            val mid = buffer.get(at + 1).toInt() and 0xFF
            val high = buffer.get(at + 2).toInt() // signed, so this sign-extends
            ((high shl 16) or (mid shl 8) or low) / 8_388_608f
        }
        ENCODING_PCM_32BIT -> buffer.getInt(at) / 2_147_483_648f
        else -> buffer.getShort(at) / 32_768f
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        ENCODING_PCM_24BIT_PACKED -> 3
        ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    private fun frameCount(byteCount: Int, channels: Int, encoding: Int): Int {
        val frameBytes = bytesPerSample(encoding) * channels
        return if (frameBytes == 0) 0 else byteCount / frameBytes + 1
    }

    private fun ensureCapacity(array: ShortArray, needed: Int): ShortArray =
        if (array.size >= needed) array else ShortArray(needed)

    /**
     * [MediaFormat.containsKey] needs API 29, so this probes instead - an
     * absent key means the decoder is handing out plain 16-bit PCM.
     */
    private fun MediaFormat.pcmEncoding(): Int =
        runCatching { getInteger(MediaFormat.KEY_PCM_ENCODING) }
            .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)

    private const val TIMEOUT_US = 10_000L
    private const val CHUNK_BYTES = 256 * 1024
    private const val MAX_IDLE_ROUNDS = 500

    // AudioFormat.ENCODING_PCM_24BIT_PACKED / _32BIT are API 31 constants;
    // inlined here so this file still compiles against minSdk 26.
    private const val ENCODING_PCM_24BIT_PACKED = 21
    private const val ENCODING_PCM_32BIT = 22
}
