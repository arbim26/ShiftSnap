package com.example.shiftsnap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.ImageView

/**
 * Helper memuat foto kondisi mesin dari URI (FileProvider) ke ImageView.
 *
 * - Di-downsample (inSampleSize) agar foto kamera beresolusi besar tidak OOM.
 * - Orientasi dikoreksi memakai EXIF supaya foto tidak miring.
 *
 * @return true jika foto berhasil ditampilkan.
 */
object PhotoLoader {

    fun load(imageView: ImageView, uriString: String, reqWidth: Int = 1080): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val resolver = imageView.context.contentResolver
            val uri = Uri.parse(uriString)

            // 1) Baca dimensi saja
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2

            // 2) Decode sebenarnya
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            // 3) Koreksi rotasi EXIF
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            imageView.setImageBitmap(bmp)
            true
        } catch (e: Exception) {
            // File dihapus / izin hilang
            false
        }
    }
}
