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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roshambo.app.camera.HandGestureAnalyzer
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

enum class SizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberSizeClass(): SizeClass {
    BoxWithConstraints {
        val w = maxWidth
        return remember(w) {
            when {
                w < 600.dp -> SizeClass.COMPACT
                w < 840.dp -> SizeClass.MEDIUM
                else -> SizeClass.EXPANDED
            }
        }
    }
}

@Composable
fun rememberCameraPermission(onResult: (Boolean) -> Unit): Boolean {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
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

    val hasCamera = remember {
        LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val permission = rememberCameraPermission { viewModel.setPermission(it) }

    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // One analyzer per composition, released with it.
    val analyzer = remember {
        HandGestureAnalyzer(
            context = ctx,
            onLive = { viewModel.reportLive(it) },
            onStable = { viewModel.onGestureAccepted(it) },
        )
    }
    DisposableEffect(Unit) { onDispose { analyzer.release() } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Canvas)
    ) {
        if (sizeClass == SizeClass.COMPACT) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Header(state, sizeClass)
                CameraPane(
                    state = state,
                    analyzer = analyzer,
                    lifecycleOwner = lifecycleOwner,
                    enabled = permission && hasCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .aspectRatio(3f / 4f),
                    onCameraReady = { viewModel.setCameraReady(it) },
                )
                Spacer(Modifier.height(20.dp))
                ControlPane(
                    state = state,
                    viewModel = viewModel,
                    sizeClass = sizeClass,
                    cameraAvailable = permission && hasCamera,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(32.dp))
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    Header(state, sizeClass)
                    Spacer(Modifier.height(20.dp))
                    CameraPane(
                        state = state,
                        analyzer = analyzer,
                        lifecycleOwner = lifecycleOwner,
                        enabled = permission && hasCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                        onCameraReady = { viewModel.setCameraReady(it) },
                    )
                }
                Spacer(Modifier.width(if (sizeClass == SizeClass.EXPANDED) 36.dp else 24.dp))
                ControlPane(
                    state = state,
                    viewModel = viewModel,
                    sizeClass = sizeClass,
                    cameraAvailable = permission && hasCamera,
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun Header(state: DuelState, sizeClass: SizeClass) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("猜拳", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "摄像头出拳 · 实时识别",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
        }
        ScorePill(state)
    }
}

@Composable
private fun ScorePill(state: DuelState) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreItem(state.wins.toString(), WinGreen)
        Text("·", color = InkMuted)
        ScoreItem(state.draws.toString(), DrawAmber)
        Text("·", color = InkMuted)
        ScoreItem(state.losses.toString(), LoseRed)
    }
}

@Composable
private fun ScoreItem(value: String, color: Color) {
    Text(value, color = color, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
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
            .background(Color(0xFF17181C))
    ) {
        if (enabled) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        // COMPATIBLE avoids black-frame flicker that PERFORMANCE shows on some tablets.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { view ->
                    analyzer.bind(lifecycleOwner, view)
                    onCameraReady(true)
                },
            )
        } else {
            CameraPlaceholder()
        }

        // Live recognition hint
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = when {
                    !enabled -> "摄像头不可用"
                    state.liveGesture != Gesture.UNKNOWN -> "识别到：${state.liveGesture.label}"
                    else -> "把手放进画面"
                },
                color = Color(0xFFEDE9E2),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }
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
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ControlPane(
    state: DuelState,
    viewModel: DuelViewModel,
    sizeClass: SizeClass,
    cameraAvailable: Boolean,
    modifier: Modifier,
) {
    val target = when (sizeClass) {
        SizeClass.EXPANDED -> 72.dp
        SizeClass.MEDIUM -> 64.dp
        SizeClass.COMPACT -> 56.dp
    }

    Column(modifier.padding(top = if (sizeClass == SizeClass.COMPACT) 0.dp else 44.dp)) {
        Text(
            "选择模式",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DuelMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    selected = state.mode == mode,
                    enabled = !state.spinning,
                    modifier = Modifier.weight(1f),
                ) { viewModel.setMode(mode) }
            }
        }

        Spacer(Modifier.height(28.dp))
        VsPanel(state, target)
        Spacer(Modifier.height(20.dp))

        Text(
            state.hint,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { if (state.phase == RoundState.RESOLVED) viewModel.nextRound() else viewModel.startRound() },
            enabled = !state.spinning,
            modifier = Modifier.fillMaxWidth().height(target),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Color(0xFFF7F5F2),
                disabledContainerColor = Hairline,
                disabledContentColor = InkMuted,
            ),
        ) {
            Text(
                when {
                    state.spinning -> "识别中…"
                    state.phase == RoundState.RESOLVED -> "再来一局"
                    else -> "开始"
                },
                style = androidx.compose.material3.typography.titleLarge,
            )
        }

        if (!cameraAvailable) {
            Spacer(Modifier.height(20.dp))
            Text("手动出拳", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Gesture.ROCK, Gesture.SCISSORS, Gesture.PAPER).forEachIndexed { i, g ->
                    ManualButton(g, accentFor(i), Modifier.weight(1f), 52.dp) {
                        if (state.phase == RoundState.RESOLVED) viewModel.nextRound()
                        viewModel.startRound()
                        viewModel.playManual(g)
                    }
                }
            }
        }
    }
}

@Composable
private fun VsPanel(state: DuelState, target: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HandSlot("你", state.userGesture, state.spinning, target)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VsBadge(state.outcome)
            Spacer(Modifier.height(8.dp))
            Text(
                "第 ${state.round + 1} 局",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
        }
        HandSlot("我", state.appGesture, state.spinning, target)
    }
}

@Composable
private fun HandSlot(label: String, gesture: Gesture, spinning: Boolean, target: Dp) {
    val transition = rememberInfiniteTransition(label = "spin")
    val offset by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(160),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "offset",
    )
    val shown = if (spinning && gesture != Gesture.UNKNOWN) Gesture.UNKNOWN else gesture
    val bobbing = spinning && gesture != Gesture.UNKNOWN

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(target),
            contentAlignment = Alignment.Center,
        ) {
            GestureGlyph(
                gesture = shown,
                tint = if (shown == Gesture.UNKNOWN) InkMuted else accentFor(gesture.ordinal),
                size = target,
                // A small horizontal shake reads as "spinning" without a real animation.
                modifier = if (bobbing) Modifier.graphicsLayer { translationX = offset } else Modifier,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (spinning && gesture != Gesture.UNKNOWN) "…" else gesture.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = InkMuted,
        )
    }
}

@Composable
private fun VsBadge(outcome: Outcome?) {
    val (label, color) = when (outcome) {
        null -> "VS" to InkMuted
        Outcome.WIN -> "赢" to WinGreen
        Outcome.LOSE -> "输" to LoseRed
        Outcome.DRAW -> "平" to DrawAmber
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ModeCard(
    mode: DuelMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = if (mode == DuelMode.HEAD_TO_HEAD) RockAccent else ScissorsAccent
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.08f) else Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Hairline,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                mode.title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = if (enabled) Ink else InkMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            mode.tagline,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = if (enabled) InkMuted else InkMuted.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            mode.description,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = InkMuted.copy(alpha = 0.75f),
        )
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
            Text(gesture.label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        }
    }
}
