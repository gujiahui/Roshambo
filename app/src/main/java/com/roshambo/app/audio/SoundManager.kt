package com.roshambo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.roshambo.app.R
import com.roshambo.app.game.DuelEvent

/**
 * Thin wrapper over SoundPool that maps [DuelEvent]s to short, embedded WAV cues.
 *
 * Playback uses the GAME/SONIFICATION stream so cues are snappy and respect the
 * device's media volume — we never touch the system volume ourselves.
 */
class SoundManager(context: Context) {

    var muted: Boolean = false

    private val pool: SoundPool
    private val ids = HashMap<DuelEvent, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        ids[DuelEvent.Tap] = pool.load(context, R.raw.snd_tap, 1)
        ids[DuelEvent.Tick] = pool.load(context, R.raw.snd_tick, 1)
        ids[DuelEvent.Go] = pool.load(context, R.raw.snd_go, 1)
        ids[DuelEvent.Win] = pool.load(context, R.raw.snd_win, 1)
        ids[DuelEvent.Lose] = pool.load(context, R.raw.snd_lose, 1)
        ids[DuelEvent.Draw] = pool.load(context, R.raw.snd_draw, 1)
    }

    fun play(event: DuelEvent) {
        if (muted) return
        val id = ids[event] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = runCatching { pool.release() }
}
