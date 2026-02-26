package com.villazon.cybercam3

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PdfFile(val uri: Uri, val name: String)

object DocumentHelper {
    fun getSavedPdfs(context: Context): List<PdfFile> {
        val pdfs = mutableListOf<PdfFile>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("%CyberCam3%", "application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                pdfs.add(PdfFile(contentUri, name))
            }
        }
        return pdfs
    }

    // NUEVA FUNCIÓN: Abrir el PDF con el lector del sistema
    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No tienes una app para abrir PDFs instalada", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePdf(context: Context, uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir Documento"))
    }

    fun deletePdf(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) { e.printStackTrace() }
    }
}

@Composable
fun DocumentListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var documentList by remember { mutableStateOf(DocumentHelper.getSavedPdfs(context)) }

    // Estado para controlar qué PDF se quiere borrar y si mostramos la alerta
    var pdfToDelete by remember { mutableStateOf<PdfFile?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) {
            Text("⬅ Menú")
        }

        Text("Mis Documentos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (documentList.isEmpty()) {
            Text("No hay documentos.", modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(documentList) { pdf ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { DocumentHelper.openPdf(context, pdf.uri) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = pdf.name, fontWeight = FontWeight.Bold)
                                Text(text = "Toca para abrir", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row {
                                IconButton(onClick = { DocumentHelper.openPdf(context, pdf.uri) }) {
                                    Icon(Icons.Default.RemoveRedEye, contentDescription = "Ver", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { DocumentHelper.sharePdf(context, pdf.uri) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir")
                                }
                                IconButton(onClick = {
                                    // En lugar de borrar directo, abrimos la alerta
                                    pdfToDelete = pdf
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIÁLOGO DE CONFIRMACIÓN DE ELIMINACIÓN ---
    if (pdfToDelete != null) {
        AlertDialog(
            onDismissRequest = { pdfToDelete = null },
            title = { Text("Eliminar Documento") },
            text = { Text("¿Estás seguro de que deseas eliminar '${pdfToDelete?.name}'? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        pdfToDelete?.let { pdf ->
                            DocumentHelper.deletePdf(context, pdf.uri)
                            documentList = DocumentHelper.getSavedPdfs(context) // Refrescar la lista
                        }
                        pdfToDelete = null // Cerrar alerta
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pdfToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}