package com.paranormal.toolkit.features.ghostbox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Streams continuously demodulated audio out through the speaker/headphones. */
class LiveAudioSink(sampleRateHz: Int) {

    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ) * 4,
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    fun start() = track.play()

    fun write(samples: FloatArray) {
        val shorts = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        track.write(shorts, 0, shorts.size, AudioTrack.WRITE_BLOCKING)
    }

    fun stop() {
        runCatching { track.stop() }
        track.release()
    }
}
