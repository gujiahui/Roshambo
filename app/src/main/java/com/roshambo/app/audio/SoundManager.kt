package com.roshambo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.roshambo.app.R
import com.roshambo.app.game.DuelEvent

/**
 * Thin wrapper over SoundPool that maps [DuelEvent]s to short, embedded cues.
 *
 * Two layers per result:
 *  - a short synthesized chime (snd_*) for instant feedback, and
 *  - a natural Chinese voice line (voice_*) generated offline with edge-tts, so
 *    the spoken result works on every device without a system Chinese TTS pack.
 *
 * Playback uses the MEDIA stream so cues respect the device's media volume. We
 * deliberately avoid USAGE_GAME — on many ROMs (notably Xiaomi / gaming phones)
 * that routes to a separate "game audio" volume the user never touches, which
 * makes cues silently disappear. We never touch the system volume ourselves.
 */
class SoundManager(context: Context) {

    var muted: Boolean = false

    private val pool: SoundPool
    private val ids = HashMap<DuelEvent, Int>()
    private val voiceIds = HashMap<DuelEvent, Int>()
    private val handler = Handler(Looper.getMainLooper())

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
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

        // Embedded, offline Chinese voice lines (edge-tts). Spoken result works
        // on every device regardless of whether a system Chinese TTS pack exists.
        voiceIds[DuelEvent.Win] = pool.load(context, R.raw.voice_win, 1)
        voiceIds[DuelEvent.Lose] = pool.load(context, R.raw.voice_lose, 1)
        voiceIds[DuelEvent.Draw] = pool.load(context, R.raw.voice_draw, 1)
    }

    fun play(event: DuelEvent) {
        if (muted) return
        // Instant chime.
        ids[event]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        // Natural spoken result, layered just after the chime so it reads clearly.
        voiceIds[event]?.let { vid ->
            handler.postDelayed({
                if (!muted) pool.play(vid, 1f, 1f, 1, 0, 1f)
            }, 380)
        }
    }

    fun release() {
        runCatching { handler.removeCallbacksAndMessages(null) }
        runCatching { pool.release() }
    }
}
