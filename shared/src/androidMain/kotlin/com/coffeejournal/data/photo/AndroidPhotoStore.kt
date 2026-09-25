package com.coffeejournal.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class AndroidPhotoStore(context: Context) : PhotoStore {
    private val dir: File = File(context.applicationContext.filesDir, "photos").apply { mkdirs() }

    override fun pathFor(fileName: String): String = File(dir, fileName).absolutePath

    override suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val jpeg = downscaleToJpeg(bytes, PhotoStore.MAX_EDGE_PX, PhotoStore.JPEG_QUALITY)
        val name = UUID.randomUUID().toString().replace("-", "") + ".jpg"
        File(dir, name).writeBytes(jpeg)
        name
    }

    override suspend fun readBytes(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = File(dir, fileName)
        if (f.exists()) f.readBytes() else null
    }

    override suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { File(dir, fileName).takeIf { it.exists() }?.delete() }
    }

    override suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) { File(dir, fileName).exists() }

    companion object {
        /** Decodes with sub-sampling, applies EXIF rotation and re-encodes as JPEG. */
        fun downscaleToJpeg(bytes: ByteArray, maxEdge: Int, quality: Int): ByteArray {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (longest / (sample * 2) >= maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return bytes
            val scale = maxEdge.toFloat() / max(bmp.width, bmp.height)
            if (scale < 1f) {
                val w = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                val h = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                bmp = Bitmap.createScaledBitmap(bmp, w, h, true)
            }
            val rotation = runCatching {
                when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return out.toByteArray()
        }
    }
}
