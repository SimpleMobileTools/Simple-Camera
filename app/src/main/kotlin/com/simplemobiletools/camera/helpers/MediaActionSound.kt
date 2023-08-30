package com.simplemobiletools.camera.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RawRes
import com.simplemobiletools.camera.R
import java.io.File

/**
 * Inspired by [android.media.MediaActionSound]
 */
class MediaActionSound(private val context: Context) {
    companion object {
        val SHUTTER_CLICK = MediaSound.ManufacturerSound("camera_click.ogg")
        val FOCUS_COMPLETE = MediaSound.ManufacturerSound("camera_focus.ogg")
        val START_VIDEO_RECORDING = MediaSound.ManufacturerSound("VideoRecord.ogg")
        val STOP_VIDEO_RECORDING = MediaSound.ManufacturerSound("VideoStop.ogg")
        val TIMER_COUNTDOWN = MediaSound.RawResSound(R.raw.beep)
        val TIMER_COUNTDOWN_2_SECONDS = MediaSound.RawResSound(R.raw.beep_2_secs)

        private const val NUM_MEDIA_SOUND_STREAMS = 1
        private val SOUND_DIRS = arrayOf("/product/media/audio/ui/", "/system/media/audio/ui/")
        private const val TAG = "MediaActionSound"
        private const val STATE_NOT_LOADED = 0
        private const val STATE_LOADING = 1
        private const val STATE_LOADING_PLAY_REQUESTED = 2
        private const val STATE_LOADED = 3
        private val SOUNDS = arrayOf(SHUTTER_CLICK, FOCUS_COMPLETE, START_VIDEO_RECORDING, STOP_VIDEO_RECORDING, TIMER_COUNTDOWN, TIMER_COUNTDOWN_2_SECONDS)
    }

    sealed class MediaSound {
        class ManufacturerSound(val fileName: String, var path: String = "") : MediaSound()
        class RawResSound(@RawRes val resId: Int) : MediaSound()
    }

    private class SoundState(
        val mediaSound: MediaSound?,
        // 0 is an invalid sample ID.
        var loadId: Int = 0,
        var streamId: Int = 0,
        var state: Int = STATE_NOT_LOADED
    )

    private var soundPool: SoundPool? = SoundPool.Builder().setMaxStreams(NUM_MEDIA_SOUND_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private var mediaPlayer: MediaPlayer? = null
    private var playCompletionRunnable: Runnable? = null

    private val sounds = SOUNDS.map { SoundState(it) }

    private val playTimeHandler = Handler(Looper.getMainLooper())
    private val mLoadCompleteListener = SoundPool.OnLoadCompleteListener { _, sampleId, status ->
        for (sound in sounds) {
            if (sound.loadId != sampleId) {
                continue
            }

            var soundToBePlayed: SoundState? = null
            synchronized(sound) {
                if (status != 0) {
                    sound.state = STATE_NOT_LOADED
                    sound.loadId = 0
                    Log.e(TAG, "OnLoadCompleteListener() error: $status loading sound: ${sound.mediaSound}")
                    return@OnLoadCompleteListener
                }
                when (sound.state) {
                    STATE_LOADING -> sound.state = STATE_LOADED
                    STATE_LOADING_PLAY_REQUESTED -> {
                        soundToBePlayed = sound
                        sound.state = STATE_LOADED
                    }

                    else -> Log.e(TAG, "OnLoadCompleteListener() called in wrong state: ${sound.state} for sound: ${sound.mediaSound}")
                }
            }

            if (soundToBePlayed != null) {
                playWithSoundPool(soundToBePlayed!!)
            }
            break
        }
    }

    init {
        soundPool!!.setOnLoadCompleteListener(mLoadCompleteListener)
    }

    private fun loadSound(sound: SoundState): Int {
        var id = 0
        if (sound.mediaSound == null || soundPool == null) {
            return 0
        }

        when (sound.mediaSound) {
            is MediaSound.ManufacturerSound -> {
                for (soundDir in SOUND_DIRS) {
                    val soundPath = soundDir + sound.mediaSound.fileName
                    sound.mediaSound.path = soundPath
                    id = soundPool!!.load(soundPath, 1)
                    break
                }
            }

            is MediaSound.RawResSound -> {
                id = soundPool!!.load(context, sound.mediaSound.resId, 1)
            }
        }

        if (id > 0) {
            sound.state = STATE_LOADING
            sound.loadId = id
            return id
        }

        return 0
    }

    fun load(mediaSound: MediaSound) {
        val sound = sounds.firstOrNull() { it.mediaSound == mediaSound } ?: return
        synchronized(sound) {
            when (sound.state) {
                STATE_NOT_LOADED -> {
                    loadSound(sound).let { soundId ->
                        if (soundId <= 0) {
                            Log.e(TAG, "load() error loading sound: $mediaSound")
                        }
                    }
                }

                else -> Log.e(TAG, "load() called in wrong state: $sound for sound: $mediaSound")
            }
        }
    }

    fun play(mediaSound: MediaSound, onPlayComplete: (() -> Unit)? = null) {
        removeHandlerCallbacks()
        if (onPlayComplete != null) {
            playCompletionRunnable = Runnable {
                onPlayComplete.invoke()
            }
        }
        val sound = sounds.first { it.mediaSound == mediaSound }
        synchronized(sound) {
            when (sound.state) {
                STATE_NOT_LOADED -> {
                    val soundId = loadSound(sound)
                    if (soundId <= 0) {
                        Log.e(TAG, "play() error loading sound: $mediaSound")
                        onPlayComplete?.invoke()
                    } else {
                        sound.state = STATE_LOADING_PLAY_REQUESTED
                    }
                }

                STATE_LOADING -> sound.state = STATE_LOADING_PLAY_REQUESTED
                STATE_LOADED -> {
                    playWithSoundPool(sound)
                }

                else -> Log.e(TAG, "play() called in wrong state: ${sound.state} for sound: $mediaSound")
            }
        }
    }

    private fun playWithSoundPool(sound: SoundState) {
        if (playCompletionRunnable != null && sound.mediaSound != null) {
            val duration = getSoundDuration(sound.mediaSound)
            playTimeHandler.postDelayed(playCompletionRunnable!!, duration)
        }
        val streamId = soundPool!!.play(sound.loadId, 1.0f, 1.0f, 0, 0, 1.0f)
        sound.streamId = streamId
    }

    private fun getSoundDuration(mediaSound: MediaSound): Long {
        releaseMediaPlayer()
        mediaPlayer = when (mediaSound) {
            is MediaSound.ManufacturerSound -> MediaPlayer.create(context, Uri.fromFile(File(mediaSound.path)))
            is MediaSound.RawResSound -> MediaPlayer.create(context, mediaSound.resId)
        }
        return mediaPlayer!!.duration.toLong()
    }

    fun stop(mediaSound: MediaSound) {
        val sound = sounds.first { it.mediaSound == mediaSound }
        synchronized(sound) {
            when (sound.state) {
                STATE_LOADED -> {
                    soundPool!!.stop(sound.streamId)
                }

                else -> Log.w(TAG, "stop() should be called after sound is loaded for sound: $mediaSound")
            }
        }
    }

    fun release() {
        if (soundPool != null) {
            for (sound in sounds) {
                synchronized(sound) {
                    sound.state = STATE_NOT_LOADED
                    sound.loadId = 0
                    sound.streamId = 0
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
}
