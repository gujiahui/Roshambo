package com.roshambo.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roshambo.app.audio.SoundManager
import com.roshambo.app.camera.HandGestureAnalyzer
import com.roshambo.app.game.DuelEvent
import com.roshambo.app.game.DuelMode
import com.roshambo.app.game.DuelState
import com.roshambo.app.game.DuelViewModel
import com.roshambo.app.game.Outcome
import com.roshambo.app.game.RoundState
import com.roshambo.app.gesture.Gesture
import com.roshambo.app.ui.theme.Canvas
import com.roshambo.app.ui.theme.DrawAmber
import com.roshambo.app.ui.theme.Hairline
import com.roshambo.app.ui.theme.Ink
import com.roshambo.app.ui.theme.InkMuted
import com.roshambo.app.ui.theme.LoseRed
import com.roshambo.app.ui.theme.RockAccent
import com.roshambo.app.ui.theme.ScissorsAccent
import com.roshambo.app.ui.theme.Surface
import com.roshambo.app.ui.theme.WinGreen
import com.roshambo.app.ui.theme.accentFor
import androidx.compose.material3.MaterialTheme

enum class SizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberSizeClass(): SizeClass {
    val w = LocalConfiguration.current.screenWidthDp.dp
    return remember(w) {
        when {
            w < 600.dp -> SizeClass.COMPACT
            w < 840.dp -> SizeClass.MEDIUM
            else -> SizeClass.EXPANDED
        }
    }
}

@Composable
fun rememberCameraPermission(onResult: (Boolean) -> Unit): Boolean {
    val ctx = LocalContext.current
    var granted by remember {
        androidx.compose.runtime.mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        onResult(result)
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    return granted
}

@Composable
fun RoshamboApp(viewModel: DuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sizeClass = rememberSizeClass()

    val ctx = LocalContext.current
    val hasCamera = remember {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val permission = rememberCameraPermission { viewModel.setPermission(it) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraAvailable = permission && hasCamera

    val analyzer = remember {
        HandGestureAnalyzer(
            context = ctx,
            onLive = { viewModel.reportLive(it) },
            onStable = { viewModel.onGestureAccepted(it) },
        )
    }
    DisposableEffect(Unit) { onDispose { analyzer.release() } }

    // A fresh filter whenever a round opens, so a held gesture cannot leak across.
    LaunchedEffect(state.phase) {
        if (state.shooting) analyzer.resetFilter()
    }

    // Sound: collect VM cues and play them through the SoundManager.
    val sound = remember { SoundManager(ctx) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            sound.muted = state.muted
            sound.play(event)
        }
    }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    val target = when (sizeClass) {
        SizeClass.EXPANDED -> 84.dp
        SizeClass.MEDIUM -> 76.dp
        SizeClass.COMPACT -> 66.dp
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Canvas)) {
        if (sizeClass == SizeClass.COMPACT) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                TopBar(state, viewModel)
                Spacer(Modifier.height(14.dp))
                DuelBoard(state, target)
                Spacer(Modifier.height(16.dp))
                CameraPane(
                    state = state,
                    analyzer = analyzer,
                    lifecycleOwner = lifecycleOwner,
                    enabled = cameraAvailable,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    onCameraReady = { viewModel.setCameraReady(it) },
                )
                Spacer(Modifier.height(16.dp))
                ModeControl(state, viewModel, cameraAvailable)
                Spacer(Modifier.height(14.dp))
                ActionButton(state, viewModel)
                if (!cameraAvailable) {
                    Spacer(Modifier.height(16.dp))
                    ManualRow(viewModel)
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1.05f).fillMaxHeight()) {
                    TopBar(state, viewModel)
                    Spacer(Modifier.height(16.dp))
                    DuelBoard(state, target)
                    Spacer(Modifier.height(18.dp))
                    CameraPane(
                        state = state,
                        analyzer = analyzer,
                        lifecycleOwner = lifecycleOwner,
                        enabled = cameraAvailable,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                        onCameraReady = { viewModel.setCameraReady(it) },
                    )
                }
                Spacer(Modifier.width(28.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    ModeControl(state, viewModel, cameraAvailable)
                    Spacer(Modifier.height(18.dp))
                    ActionButton(state, viewModel)
                    if (!cameraAvailable) {
                        Spacer(Modifier.height(18.dp))
                        ManualRow(viewModel)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: DuelState, viewModel: DuelViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("石头剪刀布", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "摄像头出拳 · 实时识别",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        ScorePill(state)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { viewModel.setMuted(!state.muted) }) {
            Icon(
                imageVector = if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (state.muted) "取消静音" else "静音",
                tint = Ink,
            )
        }
    }
}

@Composable
private fun ScorePill(state: DuelState) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreItem(state.wins.toString(), WinGreen)
        Text("胜", color = InkMuted, style = MaterialTheme.typography.labelLarge)
        ScoreItem(state.draws.toString(), DrawAmber)
        Text("平", color = InkMuted, style = MaterialTheme.typography.labelLarge)
        ScoreItem(state.losses.toString(), LoseRed)
        Text("负", color = InkMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ScoreItem(value: String, color: Color) {
    Text(value, color = color, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun DuelBoard(state: DuelState, target: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HandSlot("你", state.userGesture, target, revealed = state.phase == RoundState.RESOLVED)
        CenterOutcome(state)
        HandSlot("我", state.appGesture, target, revealed = state.phase == RoundState.RESOLVED)
    }
}

@Composable
private fun HandSlot(label: String, gesture: Gesture, target: Dp, revealed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(target), contentAlignment = Alignment.Center) {
            if (gesture == Gesture.UNKNOWN) {
                Text(
                    "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkMuted,
                )
            } else {
                GestureGlyph(
                    gesture = gesture,
                    tint = accentFor(gesture.ordinal),
                    size = target,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (gesture == Gesture.UNKNOWN) "—" else gesture.label,
            style = MaterialTheme.typography.labelLarge,
            color = InkMuted,
        )
    }
}

@Composable
private fun CenterOutcome(state: DuelState) {
    val (text, color) = when {
        state.phase == RoundState.RESOLVED && state.outcome == Outcome.WIN -> "你赢" to WinGreen
        state.phase == RoundState.RESOLVED && state.outcome == Outcome.LOSE -> "你输" to LoseRed
        state.phase == RoundState.RESOLVED && state.outcome == Outcome.DRAW -> "平局" to DrawAmber
        state.phase == RoundState.COUNTDOWN -> "准备" to InkMuted
        state.phase == RoundState.SHOOT -> "出拳" to RockAccent
        else -> "VS" to InkMuted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = color)
        Spacer(Modifier.height(4.dp))
        Text(
            "第 ${state.round + 1} 局",
            style = MaterialTheme.typography.labelLarge,
            color = InkMuted,
        )
    }
}

@Composable
private fun CameraPane(
    state: DuelState,
    analyzer: HandGestureAnalyzer,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    enabled: Boolean,
    modifier: Modifier,
    onCameraReady: (Boolean) -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF17181C)),
    ) {
        if (enabled) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewCtx ->
                    PreviewView(viewCtx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        // Bind exactly once per view — rebinding on every recomposition
                        // (liveGesture updates per frame) restarts the camera nonstop.
                        analyzer.bind(lifecycleOwner, this)
                        onCameraReady(true)
                    }
                },
            )
        } else {
            CameraPlaceholder()
        }

        // Guide ring — tells the user where to place the hand.
        if (enabled && state.phase != RoundState.RESOLVED) {
            Box(
                Modifier.align(Alignment.Center).size(150.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(999.dp)),
            )
        }

        // Phase overlay.
        when {
            !enabled -> Unit
            state.phase == RoundState.COUNTDOWN && state.countdown > 0 -> {
                Box(Modifier.align(Alignment.Center)) {
                    Text(
                        state.countdown.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
            state.phase == RoundState.SHOOT -> Box(Modifier.align(Alignment.Center)) { ShootCue() }
            state.phase == RoundState.IDLE -> {
                LiveHint(state, Modifier.align(Alignment.BottomCenter))
            }
            else -> Unit
        }
    }
}

@Composable
private fun ShootCue() {
    val t = rememberInfiniteTransition(label = "shoot")
    val scale by t.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "scale",
    )
    Text(
        "出拳！",
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.scale(scale, scale),
    )
}

@Composable
private fun LiveHint(state: DuelState, modifier: Modifier) {
    Box(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (state.liveGesture != Gesture.UNKNOWN) "识别到：${state.liveGesture.label}"
            else "把手放进画面",
            color = Color(0xFFEDE9E2),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CameraPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GestureGlyph(Gesture.UNKNOWN, InkMuted, size = 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "未授权摄像头，可用下方按钮出拳",
                color = InkMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModeControl(state: DuelState, viewModel: DuelViewModel, cameraAvailable: Boolean) {
    val enabled = !state.shooting
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Surface)
            .border(1.dp, Hairline, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DuelMode.entries.forEach { mode ->
            val selected = state.mode == mode
            val accent = if (mode == DuelMode.HEAD_TO_HEAD) RockAccent else ScissorsAccent
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(enabled = enabled) {
                        viewModel.tap()
                        viewModel.setMode(mode)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) accent else InkMuted,
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        state.mode.tagline,
        style = MaterialTheme.typography.bodyLarge,
        color = InkMuted,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ActionButton(state: DuelState, viewModel: DuelViewModel) {
    val (label, enabled) = when {
        state.phase == RoundState.RESOLVED -> "再来一局" to true
        state.phase == RoundState.IDLE -> "开始" to true
        else -> "准备中…" to false
    }
    Button(
        onClick = {
            viewModel.tap()
            if (state.phase == RoundState.RESOLVED) viewModel.playAgain()
            else viewModel.startRound()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Color(0xFFF7F5F2),
            disabledContainerColor = Hairline,
            disabledContentColor = InkMuted,
        ),
    ) {
        // Typography hard-codes titleLarge color to Ink, so it must be overridden here
        // or the text becomes invisible on the Ink-colored container (black-on-black).
        Text(label, style = MaterialTheme.typography.titleLarge, color = Color(0xFFF7F5F2))
    }
    Spacer(Modifier.height(12.dp))
    Text(
        state.hint,
        style = MaterialTheme.typography.bodyLarge,
        color = InkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ManualRow(viewModel: DuelViewModel) {
    Text("手动出拳", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Gesture.ROCK, Gesture.SCISSORS, Gesture.PAPER).forEachIndexed { i, g ->
            ManualButton(g, accentFor(i), Modifier.weight(1f), 52.dp) {
                viewModel.tap()
                viewModel.playManual(g)
            }
        }
    }
}

@Composable
private fun ManualButton(gesture: Gesture, accent: Color, modifier: Modifier, height: Dp, onClick: () -> Unit) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GestureGlyph(gesture, accent, size = 24.dp, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(8.dp))
            Text(gesture.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
