package com.coffeejournal.ui.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's API keys, each encrypted with AES-256/GCM under a key the Android Keystore generates and never lets out
 * ([ALIAS]), stored as a file per key under noBackupFilesDir/ai-keys: never in the Room database, never in a JSON,
 * cloud or device-transfer backup. androidx.security-crypto is deprecated and not used.
 *
 * File: version byte, IV length, IV, ciphertext with the GCM tag. A file that no longer decrypts (the Keystore key was
 * wiped, the file was altered) is deleted and reads as no key. [cipherKey] is the Keystore key; tests pass their own,
 * since Robolectric has no AndroidKeyStore.
 */
class AndroidSecretStore(
    context: Context,
    private val cipherKey: () -> SecretKey = ::keystoreKey,
) : SecretStore {
    private val dir = File(context.noBackupFilesDir, DIR)
    private val lock = Mutex()

    override val supported: Boolean by lazy { runCatching { cipherKey(); true }.getOrDefault(false) }

    override suspend fun get(name: String): String? = io {
        val file = fileFor(name)
        if (!file.exists()) return@io null
        try {
            val bytes = file.readBytes()
            require(bytes.size > 2 && bytes[0] == VERSION) { "unknown format" }
            val ivLength = bytes[1].toInt()
            val iv = bytes.copyOfRange(2, 2 + ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, cipherKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(bytes, 2 + ivLength, bytes.size - 2 - ivLength).decodeToString()
        } catch (e: GeneralSecurityException) {
            file.delete()
            null
        } catch (e: IllegalArgumentException) {
            file.delete()
            null
        } catch (e: IndexOutOfBoundsException) {
            file.delete()
            null
        }
    }

    override suspend fun put(name: String, value: String) = io {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, cipherKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(value.encodeToByteArray())
        dir.mkdirs()
        // written beside and renamed over, so a crash never leaves half a key
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(byteArrayOf(VERSION, iv.size.toByte()) + iv + sealed)
        if (!tmp.renameTo(fileFor(name))) {
            fileFor(name).delete()
            tmp.renameTo(fileFor(name))
        }
        Unit
    }

    override suspend fun delete(name: String) = io { fileFor(name).delete(); Unit }

    /** Where [name]'s key is kept (for the tests). */
    fun fileFor(name: String): File {
        require(Regex("[a-z0-9_]+").matches(name)) { "not a key name: $name" }
        return File(dir, "$name.bin")
    }

    private suspend fun <T> io(block: () -> T): T = lock.withLock { withContext(Dispatchers.IO) { block() } }

    companion object {
        const val DIR = "ai-keys"
        const val ALIAS = "coffeejournal.ai"
        private const val VERSION: Byte = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        /** The app's AES-256 key in the Android Keystore, generated on first use; it cannot be exported. */
        fun keystoreKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}
