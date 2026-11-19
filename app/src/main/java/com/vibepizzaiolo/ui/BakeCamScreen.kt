package com.vibepizzaiolo.ui

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vibepizzaiolo.timer.Mode
import com.vibepizzaiolo.timer.Phase
import com.vibepizzaiolo.timer.TimerState
import com.vibepizzaiolo.video.BakeCamClip
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Where the bake cam is: waiting for "vai!", filming the bake, or keeping the pull-out. */
private enum class CamStep { STARTING, READY, BAKING, PULLED, FAILED }

/** How long the bake cam films at most, and how long it goes on after "pizza out". */
private const val MAX_MS = 8 * 60_000L
private const val AFTER_OUT_MS = 2_500L

/**
 * The bake cam: the phone stands by itself facing the oven and films (picture only: the
 * microphone stays with the voice commands). It knows from the timer when the pizza went in
 * ("vai!") and came out ("pizza out"), so the reel gets the launch, the bake as a time-lapse with
 * the clock, and the pull-out, without a hand touching the phone.
 */
@Composable
fun BakeCamScreen(
    timer: TimerState,
    file: File,
    onStartBake: () -> Unit,
    onPizzaOut: () -> Unit,
    onFilmed: (BakeCamClip) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var step by remember { mutableStateOf(CamStep.STARTING) }
    var problem by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    // milliseconds of the recording, from its first frame
    var startedAt by remember { mutableStateOf(0L) }
    var launchMs by remember { mutableStateOf(-1L) }
    var outMs by remember { mutableStateOf(-1L) }
    fun now() = if (startedAt > 0) SystemClock.elapsedRealtime() - startedAt else -1L

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            recording?.stop()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val future = ProcessCameraProvider.getInstance(context)
            val provider = suspendCancellableCoroutine { cont -> future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context)) }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // 720p is plenty for a few short shots and keeps a long bake small
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            file.delete()
            val options = FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_MS).build()
            recording = capture.output.prepareRecording(context, options).start(ContextCompat.getMainExecutor(context)) { ev ->
                when (ev) {
                    is VideoRecordEvent.Start -> { startedAt = SystemClock.elapsedRealtime(); if (step == CamStep.STARTING) step = CamStep.READY }
                    is VideoRecordEvent.Finalize -> {
                        val dur = ev.recordingStats.recordedDurationNanos / 1_000_000
                        val ok = ev.error == VideoRecordEvent.Finalize.ERROR_NONE || ev.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                        if (ok && launchMs >= 0 && outMs > launchMs) {
                            onFilmed(BakeCamClip(file, dur, launchMs, outMs.coerceAtMost(dur)))
                        } else if (step != CamStep.FAILED) {
                            step = CamStep.FAILED
                            problem = if (!ok) "The camera stopped (error ${ev.error}). Try again." else "The bake was not filmed: the camera stopped after ${MAX_MS / 60_000} minutes. Open the bake cam just before the pizza goes in."
                        }
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            Log.w("BakeCam", "Camera did not start", e)
            step = CamStep.FAILED
            problem = "The camera did not start: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // the timer says when the pizza goes in ("vai!" starts it) and comes out ("pizza out" stops
    // it, also after it rang); a finished timer from the pizza before does not count as a launch
    val inOven = timer.mode == Mode.BAKE && timer.isActive
    val out = timer.phase == Phase.IDLE
    LaunchedEffect(inOven, out, step) {
        if (inOven && step == CamStep.READY) {
            launchMs = now()
            step = CamStep.BAKING
        } else if (out && step == CamStep.BAKING) {
            outMs = now()
            step = CamStep.PULLED
            delay(AFTER_OUT_MS)
            recording?.stop()
            recording = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Bake cam", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (step == CamStep.READY || step == CamStep.BAKING) {
                Text("  ● REC", color = Pal.tomato, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (step == CamStep.BAKING) {
                    Text(
                        formatClock(timer.elapsedMs), fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
                        color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                }
            }
        }
        val (title, text) = when (step) {
            CamStep.STARTING -> "Starting the camera…" to ""
            CamStep.READY -> "Ready. Say “vai!” as the pizza goes in." to
                "Stand the phone 1–2 m from the oven, facing its mouth, away from the heat. It films by itself; say “pizza out” when it comes out."
            CamStep.BAKING -> "Baking. Say “pizza out” when it comes out." to "The camera keeps filming; hands free."
            CamStep.PULLED -> "Got the pull-out!" to "Now film the pizza itself: from above, around, the rim, underneath and a slice."
            CamStep.FAILED -> "No bake cam this time" to (problem ?: "")
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (text.isNotEmpty()) Text(text, fontSize = 14.sp, color = if (step == CamStep.FAILED) Pal.tomato else Pal.muted)
        when (step) {
            CamStep.READY -> BigCamButton("Vai! Start the bake", Pal.flame, onStartBake)
            CamStep.BAKING -> BigCamButton("Pizza out", Pal.tomato, onPizzaOut)
            else -> Unit
        }
    }
}

@Composable
private fun BigCamButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Pal.ink),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) { Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
}
