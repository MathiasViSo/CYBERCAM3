package com.villazon.cybercam3

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfGenerator(private val context: Context) {

    suspend fun generatePdfFromImages(imageUris: List<Uri>, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()

                for ((index, imageUri) in imageUris.withIndex()) {
                    // 1. Obtener la imagen forzando el formato "Software" para que sea compatible con el PDF
                    var bitmap: Bitmap? = null

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                        bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // <-- LA SOLUCIÓN AL ERROR DE HARDWARE
                            decoder.setTargetSampleSize(2) // Reduce ligeramente el peso crudo
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }

                    if (bitmap != null) {
                        // 2. Escalar la imagen al tamaño de una hoja A4 estándar (Escáner real)
                        val pageWidth = 595
                        val pageHeight = 842
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, pageWidth, pageHeight, true)

                        // 3. Crear la página y dibujar la foto en el PDF
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)

                        page.canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                        pdfDocument.finishPage(page)

                        // Liberar memoria para evitar cuelgues si son muchas hojas
                        if (scaledBitmap != bitmap) {
                            bitmap.recycle()
                        }
                    }
                }

                // 4. Preparar el archivo para guardarlo en la carpeta "Documentos"
                val finalFileName = if (fileName.isNotBlank()) {
                    if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
                } else {
                    "Documento_${System.currentTimeMillis()}.pdf"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    // La ruta RELATIVE_PATH solo existe en Android 10 en adelante
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/CyberCam3")
                    }
                }

                // 5. Guardar físicamente
                val pdfUri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

                if (pdfUri != null) {
                    context.contentResolver.openOutputStream(pdfUri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                }

                pdfDocument.close()
                pdfUri

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}