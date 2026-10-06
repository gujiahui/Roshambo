package com.roshambo.app.gesture

import kotlin.math.sqrt

/** The three playable gestures, plus an explicit unknown state. */
enum class Gesture(val label: String) {
    ROCK("石头"),
    SCISSORS("剪刀"),
    PAPER("布"),
    UNKNOWN("未识别");

    /** The move that defeats this one — the winning reply. */
    fun beatenBy(): Gesture = when (this) {
        ROCK -> PAPER
        SCISSORS -> ROCK
        PAPER -> SCISSORS
        UNKNOWN -> UNKNOWN
    }

    /** The move that this one defeats — the losing reply, so the opponent wins. */
    fun defeats(): Gesture = when (this) {
        ROCK -> SCISSORS
        SCISSORS -> PAPER
        PAPER -> ROCK
        UNKNOWN -> UNKNOWN
    }

    fun beats(other: Gesture): Boolean = when (this) {
        ROCK -> other == SCISSORS
        SCISSORS -> other == PAPER
        PAPER -> other == ROCK
        UNKNOWN -> false
    }
}

/**
 * Maps 21 MediaPipe hand landmarks to a finger-count gesture.
 *
 * Both signals are required per finger — distance-from-wrist catches foreshortening,
 * tip-above-pip catches tilt. Either alone misfires regularly in practice.
 */
object GestureClassifier {

    // Landmark triples: (pip, tip) per finger, thumb measured against its mcp.
    private const val THUMB_TIP = 4
    private const val THUMB_MCP = 2
    private const val WRIST = 0
    private const val INDEX_PIP = 6; private const val INDEX_TIP = 8
    private const val MIDDLE_PIP = 10; private const val MIDDLE_TIP = 12
    private const val RING_PIP = 14; private const val RING_TIP = 16
    private const val PINKY_PIP = 18; private const val PINKY_TIP = 20

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun isExtended(
        wristX: Float, wristY: Float,
        pipX: Float, pipY: Float,
        tipX: Float, tipY: Float,
    ): Boolean {
        val byDistance = dist(wristX, wristY, tipX, tipY) > dist(wristX, wristY, pipX, pipY) * 1.15f
        val byHeight = tipY < pipY - 0.01f
        return byDistance && byHeight
    }

    /**
     * @param lm 21 normalized landmarks (x/y in 0..1, y grows downward).
     */
    fun classify(lm: List<FloatArray>): Gesture {
        if (lm.size < 21) return Gesture.UNKNOWN
        val wristX = lm[WRIST][0]; val wristY = lm[WRIST][1]

        val index = isExtended(wristX, wristY, lm[INDEX_PIP][0], lm[INDEX_PIP][1], lm[INDEX_TIP][0], lm[INDEX_TIP][1])
        val middle = isExtended(wristX, wristY, lm[MIDDLE_PIP][0], lm[MIDDLE_PIP][1], lm[MIDDLE_TIP][0], lm[MIDDLE_TIP][1])
        val ring = isExtended(wristX, wristY, lm[RING_PIP][0], lm[RING_PIP][1], lm[RING_TIP][0], lm[RING_TIP][1])
        val pinky = isExtended(wristX, wristY, lm[PINKY_PIP][0], lm[PINKY_PIP][1], lm[PINKY_TIP][0], lm[PINKY_TIP][1])

        val count = listOf(index, middle, ring, pinky).count { it }
        return when {
            count == 0 -> Gesture.ROCK
            count == 4 -> Gesture.PAPER
            // Strict: only index+middle reads as scissors, so ring+pinky does not.
            count == 2 && index && middle -> Gesture.SCISSORS
            else -> Gesture.UNKNOWN
        }
    }
}

/**
 * Per-frame classification is far too noisy to drive a game with.
 * Requires the same class to win both a consecutive-frame gate and an EMA score gate,
 * then enforces a cooldown so one held gesture cannot score twice.
 */
class GestureStabilityFilter(
    private val requiredFrames: Int = 3,
    private val emaAlpha: Float = 0.45f,
    private val scoreThreshold: Float = 0.60f,
    private val cooldownMs: Long = 1_200L,
) {
    private var last: Gesture = Gesture.UNKNOWN
    private var streak: Int = 0
    private var cooldownUntil: Long = 0L
    private val scores = HashMap<Gesture, Float>()

    fun offer(g: Gesture, nowMs: Long): Gesture {
        if (g == last) streak++ else { last = g; streak = 1 }
        scores[g] = (scores.getOrDefault(g, 0f) * (1f - emaAlpha)) + emaAlpha

        if (nowMs < cooldownUntil) return Gesture.UNKNOWN

        val stable = streak >= requiredFrames && (scores[g] ?: 0f) >= scoreThreshold
        return if (stable && g != Gesture.UNKNOWN) {
            cooldownUntil = nowMs + cooldownMs
            g
        } else {
            Gesture.UNKNOWN
        }
    }

    /** Ignore any pending gesture and restart counting — used when a round begins. */
    fun reset() {
        last = Gesture.UNKNOWN
        streak = 0
        cooldownUntil = 0L
        scores.clear()
    }
}
