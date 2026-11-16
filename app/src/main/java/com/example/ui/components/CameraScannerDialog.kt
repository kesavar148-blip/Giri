package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.IceWhite
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import java.nio.ByteBuffer

@Composable
fun CameraScannerDialog(
    onDismiss: () -> Unit,
    onCaptured: (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var promptText by remember { mutableStateOf("Analyze what you see and explain key details.") }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener(
                        {
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture,
                                )
                            } catch (e: Exception) {
                                Log.e("CameraScanner", "Camera bind failed", e)
                                cameraError = e.localizedMessage ?: "Camera unavailable on this device."
                            }
                        },
                        mainExecutor,
                    )
                    previewView
                },
            )

            // Futuristic HUD targeting reticle overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension * 0.28f
                drawCircle(
                    color = ArcCyan.copy(alpha = 0.45f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
                val tick = 22.dp.toPx()
                drawLine(ArcCyan, Offset(cx - radius - tick, cy), Offset(cx - radius + 8f, cy), strokeWidth = 2.dp.toPx())
                drawLine(ArcCyan, Offset(cx + radius - 8f, cy), Offset(cx + radius + tick, cy), strokeWidth = 2.dp.toPx())
                drawLine(ArcCyan, Offset(cx, cy - radius - tick), Offset(cx, cy - radius + 8f), strokeWidth = 2.dp.toPx())
                drawLine(ArcCyan, Offset(cx, cy + radius - 8f), Offset(cx, cy + radius + tick), strokeWidth = 2.dp.toPx())
            }

            DisposableEffect(Unit) {
                onDispose {
                    runCatching {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = DeepHudSurface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, HudBorderCyan),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "OPTICAL VISION SCANNER",
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Surface(
                    color = DeepHudSurface.copy(alpha = 0.85f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, HudBorderCyan),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_camera_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close camera scanner",
                            tint = IceWhite,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ObsidianVoid.copy(alpha = 0.88f))
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cameraError != null) {
                    Text(
                        text = cameraError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF5252),
                    )
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Visual query for JARVIS") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcCyan,
                        unfocusedBorderColor = HudBorderCyan,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedLabelColor = ArcCyan,
                        unfocusedLabelColor = SteelMuted,
                    ),
                    singleLine = true,
                )

                Button(
                    onClick = {
                        isCapturing = true
                        imageCapture.takePicture(
                            mainExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = imageProxyToScaledBitmap(image)
                                    image.close()
                                    isCapturing = false
                                    if (bmp != null) {
                                        onCaptured(bmp, promptText.ifBlank { "Analyze this camera view." })
                                    } else {
                                        cameraError = "Failed to decode captured frame."
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    cameraError = exception.localizedMessage ?: "Capture failed."
                                }
                            },
                        )
                    },
                    enabled = !isCapturing && cameraError == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("capture_analyze_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArcCyan,
                        contentColor = ObsidianVoid,
                    ),
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ObsidianVoid,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CAPTURING...")
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Analyze with JARVIS")
                    }
                }
            }
        }
    }
}

private fun imageProxyToScaledBitmap(image: ImageProxy): Bitmap? {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
    }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    val maxDim = 1024
    val scale = minOf(maxDim.toFloat() / rotated.width, maxDim.toFloat() / rotated.height, 1f)
    return if (scale < 1f) {
        Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        rotated
    }
}
