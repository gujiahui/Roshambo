package com.roshambo.app.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roshambo.app.gesture.Gesture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class DuelViewModel : ViewModel() {

    private val _state = MutableStateFlow(DuelState())
    val state: StateFlow<DuelState> = _state.asStateFlow()

    /** One-shot cues (sound / effects) for the UI to consume. */
    private val _events = MutableSharedFlow<DuelEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DuelEvent> = _events.asSharedFlow()

    private var roundJob: Job? = null

    fun setMode(mode: DuelMode) {
        if (_state.value.mode == mode) return
        cancelRound()
        _state.update {
            it.copy(
                mode = mode,
                phase = RoundState.IDLE,
                countdown = 0,
                appGesture = Gesture.UNKNOWN,
                userGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = when (mode) {
                    DuelMode.HEAD_TO_HEAD -> "点开始，准备石头剪刀布"
                    DuelMode.EASY_WIN -> "点开始，我保证让你赢"
                },
            )
        }
    }

    fun setCameraReady(ready: Boolean) = _state.update { it.copy(cameraReady = ready) }

    fun setPermission(granted: Boolean) = _state.update { it.copy(permissionGranted = granted) }

    fun setMuted(muted: Boolean) = _state.update { it.copy(muted = muted) }

    /** Live per-frame value for the on-screen hint. */
    fun reportLive(gesture: Gesture) = _state.update { it.copy(liveGesture = gesture) }

    /** UI calls this on any button press so taps feel responsive. */
    fun tap() = _events.tryEmit(DuelEvent.Tap)

    /**
     * Begin a round: a 3-2-1 countdown with tick/Go cues, then open the shoot
     * window. The user shows a hand; the app reveals its pick at the same moment.
     */
    fun startRound() {
        if (_state.value.phase != RoundState.IDLE) return
        cancelRound()
        _state.update {
            it.copy(
                phase = RoundState.COUNTDOWN,
                countdown = 3,
                appGesture = Gesture.UNKNOWN,
                userGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = "准备…",
            )
        }
        _events.tryEmit(DuelEvent.Tick)
        roundJob = viewModelScope.launch {
            delay(700); _state.update { it.copy(countdown = 2) }; _events.tryEmit(DuelEvent.Tick)
            delay(700); _state.update { it.copy(countdown = 1) }; _events.tryEmit(DuelEvent.Tick)
            delay(700)
            _state.update { it.copy(phase = RoundState.SHOOT, countdown = 0, hint = "出拳！把手亮出来") }
            _events.tryEmit(DuelEvent.Go)
            // Shoot window — if nothing is shown in time, relax back to idle.
            delay(SHOOT_WINDOW_MS)
            if (_state.value.phase == RoundState.SHOOT) {
                _state.update { it.copy(phase = RoundState.IDLE, hint = "没看清，点开始再来一次") }
            }
        }
    }

    /** After a round resolves, immediately queue the next countdown. */
    fun playAgain() {
        if (_state.value.phase != RoundState.RESOLVED) return
        cancelRound()
        _state.update {
            it.copy(
                phase = RoundState.IDLE,
                appGesture = Gesture.UNKNOWN,
                userGesture = Gesture.UNKNOWN,
                outcome = null,
            )
        }
        startRound()
    }

    /**
     * Called by the camera pipeline (or the manual fallback) once a gesture is
     * accepted. Only honored during the shoot window.
     */
    fun onGestureAccepted(gesture: Gesture): Boolean {
        if (gesture == Gesture.UNKNOWN) return false
        val s = _state.value
        if (s.phase != RoundState.SHOOT) return false

        cancelRound()
        when (s.mode) {
            // Both reveal at once: app rolls randomly.
            DuelMode.HEAD_TO_HEAD -> resolve(gesture, pick(Random.nextInt(3)))
            // App plays the move the user beats, so the user always wins.
            DuelMode.EASY_WIN -> resolve(gesture, gesture.defeats())
        }
        return true
    }

    /** Manual fallback used when the camera is unavailable or permission is denied. */
    fun playManual(gesture: Gesture) {
        if (_state.value.phase == RoundState.COUNTDOWN) return
        if (_state.value.phase == RoundState.RESOLVED) {
            _state.update {
                it.copy(
                    phase = RoundState.IDLE,
                    appGesture = Gesture.UNKNOWN,
                    userGesture = Gesture.UNKNOWN,
                    outcome = null,
                )
            }
        }
        _state.update {
            it.copy(
                phase = RoundState.SHOOT,
                appGesture = Gesture.UNKNOWN,
                userGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = "出拳！",
            )
        }
        onGestureAccepted(gesture)
    }

    private fun resolve(user: Gesture, app: Gesture) {
        val outcome = Outcome.of(user, app)
        _state.update {
            it.copy(
                phase = RoundState.RESOLVED,
                userGesture = user,
                appGesture = app,
                outcome = outcome,
                round = it.round + 1,
                wins = it.wins + if (outcome == Outcome.WIN) 1 else 0,
                losses = it.losses + if (outcome == Outcome.LOSE) 1 else 0,
                draws = it.draws + if (outcome == Outcome.DRAW) 1 else 0,
                hint = when (outcome) {
                    Outcome.WIN -> if (it.mode == DuelMode.EASY_WIN) "说好了让你赢 🙂" else "漂亮！"
                    Outcome.LOSE -> "差一点，再来"
                    Outcome.DRAW -> "想到一块去了"
                },
            )
        }
        _events.tryEmit(
            when (outcome) {
                Outcome.WIN -> DuelEvent.Win
                Outcome.LOSE -> DuelEvent.Lose
                Outcome.DRAW -> DuelEvent.Draw
            }
        )
    }

    private fun cancelRound() {
        roundJob?.cancel()
        roundJob = null
    }

    private fun pick(i: Int): Gesture = when (i) {
        0 -> Gesture.ROCK
        1 -> Gesture.SCISSORS
        else -> Gesture.PAPER
    }

    override fun onCleared() {
        cancelRound()
        super.onCleared()
    }

    companion object {
        /** How long the user has to show a hand after "出拳！" before we relax. */
        const val SHOOT_WINDOW_MS = 3500L
    }
}
