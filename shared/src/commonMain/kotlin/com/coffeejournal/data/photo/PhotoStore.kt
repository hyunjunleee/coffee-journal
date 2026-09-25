package com.coffeejournal.data.photo

/** Stores JPEG photos as files inside the app's private storage; the database only keeps file names. */
interface PhotoStore {
    /** Absolute path usable by the image loader. */
    fun pathFor(fileName: String): String

    /**
     * Down-scales, re-encodes as JPEG and persists; returns the stored file name. An upright JPEG that already fits
     * [MAX_EDGE_PX] (e.g. restored from a backup) is stored byte for byte, so round trips never degrade it.
     */
    suspend fun save(bytes: ByteArray): String

    suspend fun readBytes(fileName: String): ByteArray?

    suspend fun delete(fileName: String)

    suspend fun exists(fileName: String): Boolean

    companion object {
        const val MAX_EDGE_PX = 1280
        const val JPEG_QUALITY = 82
    }
}
