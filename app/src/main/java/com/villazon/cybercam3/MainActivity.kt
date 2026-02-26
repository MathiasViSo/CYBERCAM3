package com.villazon.cybercam3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar anuncios
        MobileAds.initialize(this) {}
        adManager = AdManager(this)
        adManager.loadRewardedAd()

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val pdfGenerator = remember { PdfGenerator(context) }

                // --- SISTEMA DE PUNTOS GUARDADOS EN MEMORIA INTERNA ---
                val sharedPreferences = context.getSharedPreferences("CyberCamPrefs", Context.MODE_PRIVATE)
                var userPoints by remember { mutableStateOf(sharedPreferences.getInt("USER_POINTS", 0)) }

                val updatePoints: (Int) -> Unit = { newPoints ->
                    userPoints = newPoints
                    sharedPreferences.edit().putInt("USER_POINTS", newPoints).apply()
                }

                // --- ESTADOS DE PANTALLA Y LÓGICA ---
                var showCamera by remember { mutableStateOf(false) }
                var showPreview by remember { mutableStateOf(false) }
                var showDocumentList by remember { mutableStateOf(false) }
                var showSaveDialog by remember { mutableStateOf(false) }

                var customFileName by remember { mutableStateOf("") }
                var isSaving by remember { mutableStateOf(false) }
                val capturedPhotos = remember { mutableStateListOf<Uri>() }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        updatePoints(userPoints - 5) // Descontamos y guardamos
                        showCamera = true
                    } else {
                        Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // --- NAVEGACIÓN PRINCIPAL ---
                    if (showDocumentList) {
                        DocumentListScreen(onBack = { showDocumentList = false })
                    } else if (showCamera) {
                        CameraScreen(
                            onImagesCaptured = { uris ->
                                showCamera = false
                                capturedPhotos.clear()
                                capturedPhotos.addAll(uris)
                                showPreview = true
                            },
                            onCancel = {
                                showCamera = false
                                updatePoints(userPoints + 5) // Devolvemos puntos si se arrepiente
                            }
                        )
                    } else if (showPreview) {
                        PreviewScreen(
                            photos = capturedPhotos,
                            onConfirm = { showSaveDialog = true },
                            onCancel = {
                                showPreview = false
                                capturedPhotos.clear()
                                updatePoints(userPoints + 5) // Devolvemos puntos si cancela
                            }
                        )
                    } else {
                        MainScreen(
                            points = userPoints,
                            onWatchAdClick = {
                                adManager.showRewardedAd(this@MainActivity) { earnedPoints ->
                                    updatePoints(userPoints + earnedPoints) // Sumamos y guardamos
                                }
                            },
                            onScanClick = {
                                if (userPoints >= 5) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        updatePoints(userPoints - 5) // Descontamos y guardamos
                                        showCamera = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                } else {
                                    Toast.makeText(context, "Debes ver un anuncio para ganar puntos", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onViewDocumentsClick = { showDocumentList = true }
                        )
                    }

                    // --- DIÁLOGO PARA NOMBRAR Y GUARDAR EL PDF ---
                    if (showSaveDialog && capturedPhotos.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { if(!isSaving) showSaveDialog = false },
                            title = { Text("Guardar Documento PDF") },
                            text = {
                                Column {
                                    Text("Escribe un nombre para tu PDF de ${capturedPhotos.size} páginas:")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = customFileName,
                                        onValueChange = { customFileName = it },
                                        label = { Text("Nombre del archivo") },
                                        singleLine = true,
                                        enabled = !isSaving
                                    )
                                    if (isSaving) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = !isSaving,
                                    onClick = {
                                        isSaving = true
                                        scope.launch {
                                            val pdfUri = pdfGenerator.generatePdfFromImages(capturedPhotos, customFileName)
                                            isSaving = false
                                            showSaveDialog = false
                                            showPreview = false
                                            capturedPhotos.clear()
                                            customFileName = ""

                                            if (pdfUri != null) {
                                                Toast.makeText(context, "¡Documento Guardado Exitosamente!", Toast.LENGTH_LONG).show()
                                                showDocumentList = true
                                            } else {
                                                Toast.makeText(context, "Error al crear PDF", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) { Text(if (isSaving) "Procesando..." else "Generar PDF") }
                            },
                            dismissButton = {
                                TextButton(enabled = !isSaving, onClick = { showSaveDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- PANTALLA: VISTA PREVIA DE HOJAS ESCANEADAS ---
@Composable
fun PreviewScreen(photos: List<Uri>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Vista Previa (${photos.size} hojas)", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(photos) { index, uri ->
                var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                LaunchedEffect(uri) {
                    withContext(Dispatchers.IO) {
                        try {
                            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                                    decoder.setTargetSampleSize(4) // Comprime solo para la vista previa
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                            bitmap = bmp.asImageBitmap()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Text("Página ${index + 1}", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (bitmap != null) {
                            Image(bitmap = bitmap!!, contentDescription = "Página", modifier = Modifier.height(200.dp))
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Descartar y Salir", color = MaterialTheme.colorScheme.error) }
            Button(onClick = onConfirm) { Text("Convertir a PDF") }
        }
    }
}

// --- PANTALLA: MENÚ PRINCIPAL MEJORADO CON TOPAPPBAR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(points: Int, onWatchAdClick: () -> Unit, onScanClick: () -> Unit, onViewDocumentsClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DocScanner Pro", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tarjeta de Puntos Elegante
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "TUS PUNTOS", fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                    Text(text = "$points", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Botones con Íconos
            Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Cámara")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear Documento (-5 pts)", fontSize = 16.sp)
            }

            if (points < 5) {
                Text(text = "Puntos insuficientes para escanear", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onViewDocumentsClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Folder, contentDescription = "Carpeta")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Mis Documentos", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(onClick = onWatchAdClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Anuncio (+10 pts)", fontSize = 16.sp)
            }
        }
    }
}