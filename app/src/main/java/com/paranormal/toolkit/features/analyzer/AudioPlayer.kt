package com.paranormal.toolkit.features.analyzer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import kotlin.math.max

/** Plays a fully-decoded mono clip with adjustable, pitch-preserving speed. */
class AudioPlayer {

    private var track: AudioTrack? = null

    fun play(samples: FloatArray, sampleRateHz: Int, speed: Float) {
        stop()
        if (samples.isEmpty()) return

        val pcm = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSizeBytes = max(minBufferSize, pcm.size * 2)
        val newTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        newTrack.write(pcm, 0, pcm.size)
        newTrack.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
        newTrack.play()
        track = newTrack
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    fun isPlaying(): Boolean = track?.playState == AudioTrack.PLAYSTATE_PLAYING
}
