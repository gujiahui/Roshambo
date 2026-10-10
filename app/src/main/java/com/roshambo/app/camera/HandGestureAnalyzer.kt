package com.roshambo.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalUnsafeOptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.roshambo.app.gesture.Gesture
import com.roshambo.app.gesture.GestureClassifier
import com.roshambo.app.gesture.GestureStabilityFilter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HandGestureAnalyzer(
    private val context: Context,
    private val onLive: (Gesture) -> Unit,
    private val onStable: (Gesture) -> Unit,
    private val onAnomaly: ((AnomalyType, String) -> Unit)? = null,
) {
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val filter = GestureStabilityFilter()
    private val closed = AtomicBoolean(false)
    private val active = AtomicBoolean(true)
    private val processing = AtomicBoolean(false)
    private var lastAnalyzeMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val consecutiveDrops = AtomicInteger(0)
    private val lastFrameProcessedMs = AtomicLong(0L)
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (closed.get()) return
            checkWatchdog()
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private var landmarker: HandLandmarker? = null
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null

    fun resetFilter() = filter.reset()
    fun setActive(active: Boolean) = this.active.set(active)

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val camProvider = future.get()
                provider = camProvider
                if (landmarker == null) landmarker = createLandmarker()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyze) }
                camProvider.unbindAll()
                camProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis,
                )
                lastFrameProcessedMs.set(System.currentTimeMillis())
                mainHandler.removeCallbacks(watchdogRunnable)
                mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
            } catch (e: Throwable) { Log.e(TAG, "bind failed", e) }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalUnsafeOptIn::class)
    private fun analyze(imageProxy: ImageProxy) {
        if (closed.get() || !active.get()) { imageProxy.close(); return }
        if (!processing.compareAndSet(false, true)) { imageProxy.close(); trackDroppedFrame(); return }
        try {
            if (!active.get()) return
            val now = System.currentTimeMillis()
            if (now - lastAnalyzeMs < MIN_INTERVAL_MS) return
            lastAnalyzeMs = now
            val lm = landmarker ?: return
            val src = imageProxy.toBitmap()
            val degrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = if (degrees == 0) src else rotateUpright(src, degrees)
            val result = lm.detectForVideo(BitmapImageBuilder(bitmap).build(), now)
            lastFrameProcessedMs.set(System.currentTimeMillis())
            consecutiveDrops.set(0)
            val gesture = GestureClassifier.classify(result)
            filter.onGesture(gesture)?.let { stable -> mainHandler.post { onStable(stable) } }
            mainHandler.post { onLive(gesture) }
            if (degrees != 0) bitmap.recycle()
        } catch (e: Throwable) { Log.w(TAG, "analyze failed", e) }
        finally { processing.set(false); imageProxy.close() }
    }

    private fun trackDroppedFrame() {
        val drops = consecutiveDrops.incrementAndGet()
        if (drops >= MAX_CONSECUTIVE_DROPS) {
            consecutiveDrops.set(0)
            reportAnomaly(AnomalyType.FRAME_DROP_STORM, "Too many consecutive frame drops")
        }
    }

    private fun checkWatchdog() {
        if (closed.get() || !active.get()) return
        val elapsed = System.currentTimeMillis() - lastFrameProcessedMs.get()
        if (elapsed > WATCHDOG_TIMEOUT_MS) {
            reportAnomaly(AnomalyType.WATCHDOG_TIMEOUT, "No frame processed for ${elapsed}ms")
        }
    }

    private fun reportAnomaly(type: AnomalyType, message: String) {
        Log.w(TAG, "Anomaly detected: $type - $message")
        mainHandler.post { onAnomaly?.invoke(type, message) }
    }

    private fun createLandmarker(): HandLandmarker? = try {
        HandLandmarker.createFromOptions(context, HandLandmarkerOptionsHolder.build())
    } catch (_: Throwable) { null }

    private fun rotateUpright(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun release() {
        if (closed.compareAndSet(false, true)) {
            mainHandler.removeCallbacks(watchdogRunnable)
            active.set(false)
            runCatching { provider?.unbindAll() }
            runCatching { landmarker?.close() }
            landmarker = null
            analysisExecutor.shutdown()
            try {
                if (!analysisExecutor.awaitTermination(2, TimeUnit.SECONDS)) analysisExecutor.shutdownNow()
            } catch (e: InterruptedException) { analysisExecutor.shutdownNow(); Thread.currentThread().interrupt() }
        }
    }

    fun rebind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (!closed.get()) return
        closed.set(false); active.set(true); processing.set(false)
        lastAnalyzeMs = 0L; consecutiveDrops.set(0)
        lastFrameProcessedMs.set(System.currentTimeMillis())
        analysisExecutor.shutdown()
        analysisExecutor = Executors.newSingleThreadExecutor()
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
        bind(lifecycleOwner, previewView)
    }

    companion object {
        private const val TAG = "HandGestureAnalyzer"
        private const val MIN_INTERVAL_MS = 66L
        private const val WATCHDOG_INTERVAL_MS = 2000L
        private const val WATCHDOG_TIMEOUT_MS = 5000L
        private const val MAX_CONSECUTIVE_DROPS = 30
    }
}

enum class AnomalyType { FRAME_DROP_STORM, WATCHDOG_TIMEOUT }

private object HandLandmarkerOptionsHolder {
    fun build() = HandLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
        .setRunningMode(RunningMode.VIDEO)
        .setNumHands(1)
        .setMinHandDetectionConfidence(0.6f)
        .setMinHandPresenceConfidence(0.5f)
        .setMinTrackingConfidence(0.5f)
        .build()
}
