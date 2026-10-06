package com.roshambo.app.game

import com.roshambo.app.gesture.Gesture

/**
 * The two duel rules. Original names were 随机模式 / 必输模式, renamed to read as duels.
 */
enum class DuelMode(
    val title: String,
    val tagline: String,
    val description: String,
) {
    /** Random mode — the wheel spins, a stable gesture stops it. */
    HEAD_TO_HEAD(
        title = "巅峰对决",
        tagline = "势均力敌，各凭手气",
        description = "转盘不停轮换，出拳后随机迎战",
    ),

    /** Must-lose mode — the app always plays the winning reply, so the user always wins. */
    EASY_WIN(
        title = "轻松赢",
        tagline = "让着你，随便出",
        description = "识别后必出克制你的招式",
    );

    companion object {
        fun fromName(value: String?): DuelMode =
            entries.firstOrNull { it.name == value } ?: HEAD_TO_HEAD
    }
}

enum class RoundState { IDLE, SPINNING, RESOLVED }

enum class Outcome(val label: String) {
    WIN("你赢了"),
    LOSE("你输了"),
    DRAW("平局");

    companion object {
        fun of(user: Gesture, app: Gesture): Outcome = when {
            user == app -> DRAW
            user.beats(app) -> WIN
            else -> LOSE
        }
    }
}

data class DuelState(
    val mode: DuelMode = DuelMode.HEAD_TO_HEAD,
    val round: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** Raw per-frame classification, drives the live hint. */
    val liveGesture: Gesture = Gesture.UNKNOWN,
    val phase: RoundState = RoundState.IDLE,
    /** What the app is currently showing — spins in HEAD_TO_HEAD. */
    val appGesture: Gesture = Gesture.UNKNOWN,
    val userGesture: Gesture = Gesture.UNKNOWN,
    val outcome: Outcome? = null,
    val hint: String = "点击开始，摄像头识别你的出拳",
    val cameraReady: Boolean = false,
    val permissionGranted: Boolean = false,
) {
    val canStart: Boolean get() = phase == RoundState.IDLE
    val spinning: Boolean get() = phase == RoundState.SPINNING
    val totalRounds: Int get() = wins + losses + draws
}
