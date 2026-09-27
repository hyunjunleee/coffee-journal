package com.coffeejournal.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One HTTP exchange with an AI service, bound in Koin by the platform module (Android: HttpURLConnection, no extra
 * library; iOS: not connected yet). Tests bind a fake, so no test ever reaches the network.
 */
interface AiHttp {
    /** False where the platform cannot call the services (the screens then say so instead). */
    val supported: Boolean get() = true

    /**
     * Sends [request] and returns the status with the body (the error body for 4xx / 5xx). Throws [AiConnectionException]
     * when no response came back (offline, DNS, timeout).
     */
    suspend fun send(request: AiHttpRequest): AiHttpResponse
}

/** [body] null is a GET. The headers carry the user's key, so [toString] never prints them. */
class AiHttpRequest(val url: String, val headers: Map<String, String>, val body: String? = null) {
    val method: String get() = if (body == null) "GET" else "POST"

    override fun toString(): String = "$method $url (${headers.keys.joinToString()})"
}

data class AiHttpResponse(val status: Int, val body: String)

class AiConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Where the user's API keys are kept, bound in Koin by the platform module. Android: encrypted with a non-exportable
 * AES-256/GCM key in the Android Keystore, the ciphertext under noBackupFilesDir (never in the database, a JSON backup,
 * a cloud backup or a device transfer). iOS: the Keychain, readable on this device only after its first unlock (never
 * in iCloud Keychain or a backup restored to another device).
 */
interface SecretStore {
    val supported: Boolean
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String)
    suspend fun delete(name: String)
}

/** Unicode NFC (Android: java.text.Normalizer; iOS: precomposedStringWithCanonicalMapping), for the quote check. */
expect fun normalizeNfc(text: String): String

/**
 * Google Search Suggestions (searchEntryPoint.renderedContent) under a grounded Gemini answer, as Google's terms ask
 * for Google Search grounding. Android: a WebView; iOS: nothing yet.
 */
@Composable
expect fun SearchSuggestions(html: String, modifier: Modifier = Modifier)
