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
        val jpeg = if (isStorableAsIs(bytes, PhotoStore.MAX_EDGE_PX)) bytes else downscaleToJpeg(bytes, PhotoStore.MAX_EDGE_PX, PhotoStore.JPEG_QUALITY)
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
        /** A JPEG this large or smaller is never re-encoded when it already fits (1280px at q82 is ~0.3 MB). */
        const val MAX_KEEP_BYTES = 2 * 1024 * 1024

        /**
         * True when [bytes] already are what [save] would produce: an upright JPEG whose longest edge fits [maxEdge].
         * Such a photo — one restored from a backup, or a web photo (700px, q0.7) — is stored byte for byte, so every
         * backup → restore round trip keeps it identical instead of losing quality (and growing) with each re-encode.
         */
        fun isStorableAsIs(bytes: ByteArray, maxEdge: Int): Boolean {
            if (bytes.size < 4 || bytes.size > MAX_KEEP_BYTES) return false
            if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte() || bytes[2] != 0xFF.toByte()) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || max(bounds.outWidth, bounds.outHeight) > maxEdge) return false
            if (bounds.outMimeType != null && bounds.outMimeType != "image/jpeg") return false
            // an EXIF rotation is baked into the pixels by re-encoding, so only upright photos are kept as they are
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            return orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED
        }

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
