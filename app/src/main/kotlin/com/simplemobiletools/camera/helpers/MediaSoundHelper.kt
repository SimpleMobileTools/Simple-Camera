package com.simplemobiletools.camera.helpers

import android.content.Context

class MediaSoundHelper(context: Context) {
    private val mediaActionSound = MediaActionSound(context)

    fun loadSounds() {
        mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING)
        mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING)
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        mediaActionSound.load(MediaActionSound.TIMER_COUNTDOWN)
        mediaActionSound.load(MediaActionSound.TIMER_COUNTDOWN_2_SECONDS)
    }

    fun playShutterSound() {
        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun playStartVideoRecordingSound(onPlayComplete: () -> Unit) {
        mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING, onPlayComplete)
    }

    fun playStopVideoRecordingSound() {
        mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    fun playTimerCountdownSound() {
        mediaActionSound.play(MediaActionSound.TIMER_COUNTDOWN)
    }

    fun playTimerCountdown2SecondsSound() {
        mediaActionSound.play(MediaActionSound.TIMER_COUNTDOWN_2_SECONDS)
    }

    fun stopTimerCountdown2SecondsSound() {
        mediaActionSound.stop(MediaActionSound.TIMER_COUNTDOWN_2_SECONDS)
    }

    fun release() {
        mediaActionSound.release()
    }
}
