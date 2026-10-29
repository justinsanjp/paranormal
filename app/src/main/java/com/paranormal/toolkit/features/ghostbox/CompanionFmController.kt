package com.paranormal.toolkit.features.ghostbox

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Drives a real hardware FM tuner indirectly, through whichever app on the
 * device currently holds the active media session for it (verified on a
 * Xiaomi/MIUI device: their own Xiaomi-signed FM radio app registers a
 * MediaSession while tuned, and reacts to standard media-button key events
 * exactly like a headset button would). This never talks to the tuner
 * hardware directly and needs no special permission - [AudioManager]'s
 * media-key dispatch is a completely public API, the same mechanism a
 * wired headset button uses. If no such companion app is currently running
 * with FM active, these key events simply go nowhere; there is no fallback
 * that fakes a result.
 */
class CompanionFmController(private val audioManager: AudioManager) {

    fun seekNext() = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun seekPrevious() = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun togglePlayback() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    private fun sendKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }
}
