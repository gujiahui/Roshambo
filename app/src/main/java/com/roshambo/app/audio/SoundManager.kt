package com.roshambo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.roshambo.app.R
import com.roshambo.app.game.DuelEvent

class SoundManager(context: Context) {

    var muted: Boolean = false

    private val appContext = context.applicationContext
    private val pool: SoundPool
    private val ids = HashMap<DuelEvent, Int>()
    private val voiceLoaded = HashMap<DuelEvent, Boolean>()
    private val voicePlayers = HashMap<DuelEvent, MediaPlayer>()
    private val handler = Handler(Looper.getMainLooper())

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        pool.setOnLoadCompleteListener { _, _, _ -> }

        ids[DuelEvent.Tap] = pool.load(appContext, R.raw.snd_tap, 1)
        ids[DuelEvent.Tick] = pool.load(appContext, R.raw.snd_tick, 1)
        ids[DuelEvent.Go] = pool.load(appContext, R.raw.snd_go, 1)
        ids[DuelEvent.Win] = pool.load(appContext, R.raw.snd_win, 1)
        ids[DuelEvent.Lose] = pool.load(appContext, R.raw.snd_lose, 1)
        ids[DuelEvent.Draw] = pool.load(appContext, R.raw.snd_draw, 1)

        prepareVoice(DuelEvent.Win, R.raw.voice_win)
        prepareVoice(DuelEvent.Lose, R.raw.voice_lose)
        prepareVoice(DuelEvent.Draw, R.raw.voice_draw)
    }

    private fun prepareVoice(event: DuelEvent, resId: Int) {
        try {
            voiceLoaded[event] = false
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(appContext, Uri.parse("android.resource://" + appContext.packageName + "/" + resId))
            player.setOnPreparedListener {
                it.isLooping = false
                voiceLoaded[event] = true
            }
            player.setOnCompletionListener {
                it.seekTo(0)
            }
            player.prepareAsync()
            voicePlayers[event] = player
        } catch (_: Throwable) {
            voiceLoaded[event] = false
        }
    }

    fun play(event: DuelEvent) {
        if (muted) return

        ids[event]?.let { soundId ->
            handler.postDelayed({
                if (!muted) {
                    pool.play(soundId, 1f, 1f, 1, 0, 1f)
                }
            }, 50)
        }

        val player = voicePlayers[event]
        if (player != null && voiceLoaded[event] == true) {
            handler.postDelayed({
                if (!muted) {
                    try {
                        if (player.isPlaying) {
                            player.seekTo(0)
                        } else {
                            player.start()
                        }
                    } catch (_: Throwable) {
                        playVoiceViaPool(event)
                    }
                }
            }, 380)
        }
    }

    private fun playVoiceViaPool(event: DuelEvent) {
        try {
            val resId = when (event) {
                DuelEvent.Win -> R.raw.voice_win
                DuelEvent.Lose -> R.raw.voice_lose
                DuelEvent.Draw -> R.raw.voice_draw
                else -> return
            }
            val voiceId = pool.load(appContext, resId, 1)
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == voiceId) {
                    pool.play(voiceId, 1f, 1f, 1, 0, 1f)
                }
            }
        } catch (_: Throwable) { }
    }

    fun release() {
        runCatching { handler.removeCallbacksAndMessages(null) }
        runCatching { pool.release() }
        voicePlayers.values.forEach { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        voicePlayers.clear()
    }
}