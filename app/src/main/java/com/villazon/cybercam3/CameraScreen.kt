package com.villazon.cybercam3

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onImagesCaptured: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) } // Controlador de hardware
    var isFlashEnabled by remember { mutableStateOf(false) } // Estado del flash

    val capturedPhotos = remember { mutableStateListOf<Uri>() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        // Aquí guardamos el acceso al hardware de la cámara
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error", e)
                    }
                }, executor)
                previewView
            }
        )

        // --- NUEVO: BOTÓN DE LINTERNA (Arriba a la derecha) ---
        IconButton(
            onClick = {
                isFlashEnabled = !isFlashEnabled
                cameraControl?.enableTorch(isFlashEnabled) // Enciende o apaga el flash real
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (isFlashEnabled) Color.Yellow else Color.White
            )
        }

        // --- CONTROLES INFERIORES ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("X", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    takePhoto(imageCapture, context, ContextCompat.getMainExecutor(context)) { newUri ->
                        capturedPhotos.add(newUri)
                        Toast.makeText(context, "Hoja ${capturedPhotos.size} capturada", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(80.dp),
                shape = CircleShape
            ) {
                Text("📸", fontSize = 24.sp)
            }

            if (capturedPhotos.isNotEmpty()) {
                BadgedBox(
                    badge = { Badge { Text(capturedPhotos.size.toString()) } }
                ) {
                    Button(onClick = {
                        // Apagamos el flash por seguridad al salir
                        cameraControl?.enableTorch(false)
                        onImagesCaptured(capturedPhotos)
                    }) {
                        Text("Ver")
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }
        }
    }
}

private fun takePhoto(imageCapture: ImageCapture?, context: Context, executor: Executor, onPhotoTaken: (Uri) -> Unit) {
    val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture?.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onPhotoTaken(Uri.fromFile(file))
        }
        override fun onError(exception: ImageCaptureException) {}
    })
}