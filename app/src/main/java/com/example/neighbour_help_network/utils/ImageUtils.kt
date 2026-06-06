package com.example.neighbour_help_network.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    /**
     * Reads a Uri, decodes it into a Bitmap, compresses it, and returns a ByteArray.
     * This avoids uploading massive files to Firebase Storage and prevents URI read errors.
     */
    fun compressImageFromUri(context: Context, uri: Uri, maxSizeKb: Int = 300): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Calculate target dimensions (max 800x800)
            val maxDim = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDim || height > maxDim) {
                maxDim.toFloat() / Math.max(width, height)
            } else 1f

            val scaledWidth = Math.round(width * scale)
            val scaledHeight = Math.round(height * scale)

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

            var quality = 90
            var stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

            while (stream.toByteArray().size / 1024 > maxSizeKb && quality > 10) {
                stream.reset()
                quality -= 10
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }

            stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves provided image bytes into app-internal storage and returns absolute file path.
     * Directory: files/profile_photos
     */
    fun saveProfileImage(context: Context, filename: String, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "profile_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            val fos = FileOutputStream(file)
            fos.write(bytes)
            fos.flush()
            fos.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
