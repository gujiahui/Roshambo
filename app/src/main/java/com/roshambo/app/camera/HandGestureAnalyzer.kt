package com.roshambo.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX preview + MediaPipe hand analysis, wired to a stability filter.
 *
 * Owns its own analysis executor. Every ImageProxy is closed in a finally block —
 * leaking frames stalls the preview within seconds.
 */
class HandGestureAnalyzer(
    private val context: Context,
    /** Raw per-frame result, for the live on-screen hint. */
    private val onLive: (Gesture) -> Unit,
    /** Fires only after the consecutive-frame and EMA gates both pass. */
    private val onStable: (Gesture) -> Unit,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val filter = GestureStabilityFilter()
    private val closed = AtomicBoolean(false)

    private var landmarker: HandLandmarker? = null
    private var provider: ProcessCameraProvider? = null

    fun interface Listener {
        fun onFrame(gesture: Gesture)
    }

    /** Fresh filter — call when a new round starts so a held gesture cannot leak across. */
    fun resetFilter() = filter.reset()

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val camProvider = future.get()
                provider = camProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyze) }

                camProvider.unbindAll()
                camProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            } catch (t: Throwable) {
                // A device without a usable front camera must not crash the app.
                onLive(Gesture.UNKNOWN)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(imageProxy: ImageProxy) {
        try {
            if (closed.get()) return
            val lm = landmarker ?: createLandmarker() ?: return

            // Frames arrive in sensor orientation. Rotate upright first, otherwise the
            // classifier's "tip above knuckle" test is wrong on portrait phones and
            // nearly every hand reads as ROCK.
            val bitmap = rotateUpright(imageProxy.toBitmap(), imageProxy.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(bitmap).build()

            // CameraX timestamps are monotonic nanoseconds; MediaPipe VIDEO mode wants ms.
            val result: HandLandmarkerResult =
                lm.detectForVideo(mpImage, imageProxy.imageInfo.timestamp / 1_000_000)
            val first = result.landmarks().firstOrNull()

            val raw = if (first == null) Gesture.UNKNOWN
            else GestureClassifier.classify(first.map { p -> floatArrayOf(p.x(), p.y()) })

            val stable = filter.offer(raw, System.currentTimeMillis())
            onLive(raw)
            if (stable != Gesture.UNKNOWN) onStable(stable)
        } catch (_: Throwable) {
            // Drop the frame rather than tearing down the camera.
        } finally {
            imageProxy.close()
        }
    }

    private fun createLandmarker(): HandLandmarker? = try {
        HandLandmarker.createFromOptions(context, HandLandmarkerOptionsHolder.build())
    } catch (_: Throwable) {
        null
    }

    private fun rotateUpright(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun release() {
        if (closed.compareAndSet(false, true)) {
            runCatching { provider?.unbindAll() }
            runCatching { landmarker?.close() }
            landmarker = null
            analysisExecutor.shutdown()
        }
    }
}

private object HandLandmarkerOptionsHolder {
    fun build() =
        HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
}
