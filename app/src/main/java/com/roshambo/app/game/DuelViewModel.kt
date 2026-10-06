package com.roshambo.app.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roshambo.app.gesture.Gesture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class DuelViewModel : ViewModel() {

    private val _state = MutableStateFlow(DuelState())
    val state: StateFlow<DuelState> = _state.asStateFlow()

    /** Exposed so the camera analyzer can hand gestures to the same stability filter. */
    var onStableGesture: (Gesture) -> Unit = {}
        private set

    private var spinJob: Job? = null

    fun bindGestureListener(listener: (Gesture) -> Unit) {
        onStableGesture = listener
    }

    fun setMode(mode: DuelMode) {
        if (_state.value.mode == mode) return
        stopSpin()
        _state.update {
            it.copy(
                mode = mode,
                phase = RoundState.IDLE,
                appGesture = Gesture.UNKNOWN,
                userGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = when (mode) {
                    DuelMode.HEAD_TO_HEAD -> "点击开始，摄像头识别你的出拳"
                    DuelMode.EASY_WIN -> "点击开始，识别后我会出你能赢的"
                },
            )
        }
    }

    fun setCameraReady(ready: Boolean) = _state.update { it.copy(cameraReady = ready) }

    fun setPermission(granted: Boolean) = _state.update { it.copy(permissionGranted = granted) }

    /** Live per-frame value for the on-screen hint. */
    fun reportLive(gesture: Gesture) = _state.update { it.copy(liveGesture = gesture) }

    fun startRound() {
        if (!_state.value.canStart) return
        stopSpin()
        when (_state.value.mode) {
            DuelMode.HEAD_TO_HEAD -> startHeadToHead()
            DuelMode.EASY_WIN -> startEasyWin()
        }
    }

    /** Random mode: the wheel spins until a stable gesture stops it, then the app rolls. */
    private fun startHeadToHead() {
        _state.update {
            it.copy(
                phase = RoundState.SPINNING,
                userGesture = Gesture.UNKNOWN,
                appGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = "出你的拳——石头、剪刀还是布？",
            )
        }
        spinJob = viewModelScope.launch {
            val pool = listOf(Gesture.ROCK, Gesture.SCISSORS, Gesture.PAPER)
            var i = 0
            while (isActive) {
                _state.update { it.copy(appGesture = pool[i % pool.size]) }
                delay(110L)
                i++
            }
        }
    }

    /** Must-lose mode: waits for a gesture, then plays the reply the user can beat. */
    private fun startEasyWin() {
        _state.update {
            it.copy(
                phase = RoundState.SPINNING,
                userGesture = Gesture.UNKNOWN,
                appGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = "出你的拳——我一定让你赢",
            )
        }
    }

    /**
     * Called by the camera pipeline once a gesture passes the stability filter.
     * Returns true if the gesture was consumed by the current round.
     */
    fun onGestureAccepted(gesture: Gesture): Boolean {
        if (gesture == Gesture.UNKNOWN) return false
        val s = _state.value
        if (s.phase != RoundState.SPINNING) return false

        return when (s.mode) {
            DuelMode.HEAD_TO_HEAD -> {
                stopSpin()
                val app = pick(Random.nextInt(3))
                resolve(gesture, app)
                true
            }
            // Play whatever the user's move defeats, so the user always takes the round.
            DuelMode.EASY_WIN -> {
                stopSpin()
                resolve(gesture, gesture.defeats())
                true
            }
        }
    }

    /** Manual fallback used when the camera is unavailable or permission is denied. */
    fun playManual(gesture: Gesture) = onGestureAccepted(gesture)

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
    }

    fun nextRound() {
        stopSpin()
        _state.update {
            it.copy(
                phase = RoundState.IDLE,
                userGesture = Gesture.UNKNOWN,
                appGesture = Gesture.UNKNOWN,
                outcome = null,
                hint = "点击开始下一局",
            )
        }
    }

    fun resetScore() {
        _state.update { it.copy(wins = 0, losses = 0, draws = 0, round = 0) }
    }

    private fun stopSpin() {
        spinJob?.cancel()
        spinJob = null
    }

    private fun pick(i: Int): Gesture = when (i) {
        0 -> Gesture.ROCK
        1 -> Gesture.SCISSORS
        else -> Gesture.PAPER
    }

    override fun onCleared() {
        stopSpin()
        super.onCleared()
    }
}
