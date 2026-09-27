package com.coffeejournal.data.photo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.posix.memcpy

/**
 * iOS counterpart of AndroidPhotoStore: JPEG files under Documents/photos.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoStore : PhotoStore {
    private val dir: String by lazy {
        val documents = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = true, error = null,
        )
        val path = requireNotNull(documents?.path) + "/photos"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        path
    }

    override fun pathFor(fileName: String): String = "$dir/$fileName"

    override suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        val data = bytes.toNSData()
        val image = UIImage.imageWithData(data)
        // an upright JPEG that already fits (restored from a backup, a web photo) is kept byte for byte, so backup
        // round trips never lose quality or grow (same rule as AndroidPhotoStore.isStorableAsIs)
        val keep = image != null && isUprightFittingJpeg(bytes, image)
        val jpeg = if (keep) data else image?.let { UIImageJPEGRepresentation(it, PhotoStore.JPEG_QUALITY / 100.0) } ?: data
        val name = NSUUID().UUIDString.replace("-", "").lowercase() + ".jpg"
        jpeg.writeToFile(pathFor(name), atomically = true)
        name
    }

    override suspend fun readBytes(fileName: String): ByteArray? = withContext(Dispatchers.Default) {
        NSData.create(contentsOfFile = pathFor(fileName))?.toByteArray()
    }

    override suspend fun delete(fileName: String) {
        NSFileManager.defaultManager.removeItemAtPath(pathFor(fileName), error = null)
    }

    override suspend fun exists(fileName: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(pathFor(fileName))

    private fun isUprightFittingJpeg(bytes: ByteArray, image: UIImage): Boolean {
        if (bytes.size < 4 || bytes.size > MAX_KEEP_BYTES) return false
        if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte() || bytes[2] != 0xFF.toByte()) return false
        if (image.imageOrientation != UIImageOrientation.UIImageOrientationUp) return false
        val longest = image.size.useContents { maxOf(width, height) } * image.scale
        return longest > 0 && longest <= PhotoStore.MAX_EDGE_PX
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val out = ByteArray(length.toInt())
        if (out.isNotEmpty()) out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        return out
    }

    private companion object {
        const val MAX_KEEP_BYTES = 2 * 1024 * 1024
    }
}
