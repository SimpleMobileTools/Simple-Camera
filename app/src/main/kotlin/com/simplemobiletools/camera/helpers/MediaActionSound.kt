package com.simplemobiletools.camera.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Inspired by [android.media.MediaActionSound]
 */
class MediaActionSound(private val context: Context) {
    companion object {
        private const val NUM_MEDIA_SOUND_STREAMS = 1
        private val SOUND_DIRS = arrayOf(
            "/product/media/audio/ui/",
            "/system/media/audio/ui/"
        )
        private val SOUND_FILES = arrayOf(
            "camera_click.ogg",
            "camera_focus.ogg",
            "VideoRecord.ogg",
            "VideoStop.ogg"
        )
        private const val TAG = "MediaActionSound"
        const val SHUTTER_CLICK = 0
        const val START_VIDEO_RECORDING = 2
        const val STOP_VIDEO_RECORDING = 3
        private const val STATE_NOT_LOADED = 0
        private const val STATE_LOADING = 1
        private const val STATE_LOADING_PLAY_REQUESTED = 2
        private const val STATE_LOADED = 3
    }

    private class SoundState(val name: Int) {
        var id = 0 // 0 is an invalid sample ID.
        var state: Int = STATE_NOT_LOADED
        var path: String? = null
    }

    private var soundPool: SoundPool? = SoundPool.Builder()
        .setMaxStreams(NUM_MEDIA_SOUND_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private var mediaPlayer: MediaPlayer? = null
    private var playCompletionRunnable: Runnable? = null

    private val mSounds: Array<SoundState?> = arrayOfNulls(SOUND_FILES.size)
    private val playTimeHandler = Handler(Looper.getMainLooper())
    private val mLoadCompleteListener = SoundPool.OnLoadCompleteListener { _, sampleId, status ->
        for (sound in mSounds) {
            if (sound!!.id != sampleId) {
                continue
            }
            var soundToBePlayed: SoundState? = null
            synchronized(sound) {
                if (status != 0) {
                    sound.state = STATE_NOT_LOADED
                    sound.id = 0
                    Log.e(TAG, "OnLoadCompleteListener() error: $status loading sound: ${sound.name}")
                    return@OnLoadCompleteListener
                }
                when (sound.state) {
                    STATE_LOADING -> sound.state = STATE_LOADED
                    STATE_LOADING_PLAY_REQUESTED -> {
                        soundToBePlayed = sound
                        sound.state = STATE_LOADED
                    }
                    else -> Log.e(TAG, "OnLoadCompleteListener() called in wrong state: ${sound.state} for sound: ${sound.name}")
                }
            }
            if (soundToBePlayed != null) {
                playSoundPool(soundToBePlayed!!)
            }
            break
        }
    }

    init {
        soundPool!!.setOnLoadCompleteListener(mLoadCompleteListener)
        for (i in mSounds.indices) {
            mSounds[i] = SoundState(i)
        }
    }

    private fun loadSound(sound: SoundState?): Int {
        val soundFileName = SOUND_FILES[sound!!.name]
        for (soundDir in SOUND_DIRS) {
            val soundPath = soundDir + soundFileName
            sound.path = soundPath
            val id = soundPool!!.load(soundPath, 1)
            if (id > 0) {
                sound.state = STATE_LOADING
                sound.id = id
                return id
            }
        }
        return 0
    }

    fun load(soundName: Int) {
        if (soundName < 0 || soundName >= SOUND_FILES.size) {
            throw RuntimeException("Unknown sound requested: $soundName")
        }
        val sound = mSounds[soundName]
        synchronized(sound!!) {
            when (sound.state) {
                STATE_NOT_LOADED -> {
                    loadSound(sound).let { soundId ->
                        if (soundId <= 0) {
                            Log.e(TAG, "load() error loading sound: $soundName")
                        }
                    }
                }
                else -> Log.e(TAG, "load() called in wrong state: $sound for sound: $soundName")
            }
        }
    }

    fun play(soundName: Int, onPlayComplete: (() -> Unit)? = null) {
        if (soundName < 0 || soundName >= SOUND_FILES.size) {
            throw RuntimeException("Unknown sound requested: $soundName")
        }
        removeHandlerCallbacks()
        if (onPlayComplete != null) {
            playCompletionRunnable = Runnable {
                onPlayComplete.invoke()
            }
        }
        val sound = mSounds[soundName]
        synchronized(sound!!) {
            when (sound.state) {
                STATE_NOT_LOADED -> {
                    val soundId = loadSound(sound)
                    if (soundId <= 0) {
                        Log.e(TAG, "play() error loading sound: $soundName")
                    } else {
                        sound.state = STATE_LOADING_PLAY_REQUESTED
                    }
                }
                STATE_LOADING -> sound.state = STATE_LOADING_PLAY_REQUESTED
                STATE_LOADED -> {
                    playSoundPool(sound)
                }
                else -> Log.e(TAG, "play() called in wrong state: ${sound.state} for sound: $soundName")
            }
        }
    }

    private fun playSoundPool(sound: SoundState) {
        if (playCompletionRunnable != null) {
            val duration = getSoundDuration(sound.path!!)
            playTimeHandler.postDelayed(playCompletionRunnable!!, duration)
        }
        soundPool!!.play(sound.id, 1.0f, 1.0f, 0, 0, 1.0f)
    }

    fun release() {
        if (soundPool != null) {
            for (sound in mSounds) {
                synchronized(sound!!) {
                    sound.state = STATE_NOT_LOADED
                    sound.id = 0
                }
            }
            soundPool?.release()
            soundPool = null
        }
        removeHandlerCallbacks()
        releaseMediaPlayer()
    }

    private fun removeHandlerCallbacks() {
        playCompletionRunnable?.let { playTimeHandler.removeCallbacks(it) }
        playCompletionRunnable = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun getSoundDuration(soundPath: String): Long {
        releaseMediaPlayer()
        mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(soundPath)))
        return mediaPlayer!!.duration.toLong()
    }
}
