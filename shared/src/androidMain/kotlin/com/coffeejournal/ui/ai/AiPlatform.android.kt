package com.coffeejournal.ui.ai

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.coffeejournal.ui.platform.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

actual fun normalizeNfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

/**
 * Google's Search Suggestions HTML as it came (no script; the chips are links). A tapped suggestion opens Google
 * Search in the browser, as the grounding terms ask, instead of inside this view.
 */
@Composable
actual fun SearchSuggestions(html: String, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(0)
                isVerticalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        openUrl(request.url.toString())
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        modifier = modifier.fillMaxWidth().height(64.dp),
    )
}

/**
 * [AiHttp] on java.net.HttpURLConnection, which Android has built in (no HTTP library added): 15 s to connect, 120 s
 * to read (a searched answer can take a while), on the IO dispatcher. The error stream is read for 4xx / 5xx so the
 * service's own message reaches the user; bodies over [MAX_BYTES] are cut.
 */
class AndroidAiHttp(private val connectTimeoutMs: Int = 15_000, private val readTimeoutMs: Int = 120_000) : AiHttp {
    override suspend fun send(request: AiHttpRequest): AiHttpResponse = runInterruptible(Dispatchers.IO) {
        val connection = try {
            URL(request.url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw AiConnectionException(e.message ?: "connection failed", e)
        }
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.requestMethod = request.method
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val body = request.body
            if (body != null) {
                val bytes = body.encodeToByteArray()
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            AiHttpResponse(status, stream?.use { read(it) } ?: "")
        } catch (e: IOException) {
            throw AiConnectionException(e.message ?: e::class.simpleName ?: "IOException", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun read(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < MAX_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, minOf(n, MAX_BYTES - out.size()))
        }
        return out.toByteArray().decodeToString()
    }

    private companion object {
        /** Five Tavily pages with their text stay far below this. */
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
