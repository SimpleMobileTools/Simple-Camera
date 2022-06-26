package com.simplemobiletools.camera.helpers

import android.media.MediaActionSound

class MediaSoundHelper {
    private val mediaActionSound = MediaActionSound()

    fun loadSounds() {
        mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING)
        mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING)
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
    }

    fun playShutterSound() {
        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun playStartVideoRecordingSound() {
        mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING)
    }

    fun playStopVideoRecordingSound() {
        mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }
}
