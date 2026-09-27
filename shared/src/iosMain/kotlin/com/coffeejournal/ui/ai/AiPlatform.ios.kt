@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coffeejournal.ui.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.coffeejournal.ui.platform.openUrl
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGRectZero
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationTypeLinkActivated
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfc(text: String): String = (text as NSString).precomposedStringWithCanonicalMapping

/**
 * Google's Search Suggestions HTML as it came, in a WKWebView with JavaScript off (the chips are links). A tapped
 * suggestion opens Google Search in the browser, as the grounding terms ask, instead of inside this view.
 */
@Composable
actual fun SearchSuggestions(html: String, modifier: Modifier) {
    val links = remember { OpenLinksOutside() }
    val shown = remember { arrayOf<String?>(null) }
    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            config.defaultWebpagePreferences.allowsContentJavaScript = false
            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                setOpaque(false)
                scrollView.scrollEnabled = false
                navigationDelegate = links
            }
        },
        update = { view ->
            if (shown[0] != html) {
                shown[0] = html
                view.loadHTMLString(html, baseURL = null)
            }
        },
        modifier = modifier.fillMaxWidth().height(64.dp),
    )
}

/** Lets the suggestions' own HTML load and sends every tapped link to the browser. */
private class OpenLinksOutside : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        if (decidePolicyForNavigationAction.navigationType == WKNavigationTypeLinkActivated && url != null) {
            openUrl(url)
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }
}

/**
 * [AiHttp] on NSURLSession, which iOS has built in (no HTTP library added), like AndroidAiHttp on HttpURLConnection:
 * an ephemeral session (no cookies or cache on disk), 120 s for a reply (a searched answer can take a while). The
 * body of a 4xx / 5xx reply comes back too so the service's own message reaches the user; bodies over [MAX_BYTES]
 * are cut.
 */
class IosAiHttp(private val timeoutSeconds: Double = 120.0) : AiHttp {
    private val session: NSURLSession by lazy {
        val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
        config.timeoutIntervalForRequest = timeoutSeconds
        config.timeoutIntervalForResource = timeoutSeconds + 60.0
        config.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
        NSURLSession.sessionWithConfiguration(config)
    }

    override suspend fun send(request: AiHttpRequest): AiHttpResponse {
        val url = NSURL.URLWithString(request.url) ?: throw AiConnectionException("not a URL: ${request.url}")
        val req = NSMutableURLRequest.requestWithURL(url)
        req.setHTTPMethod(request.method)
        request.headers.forEach { (name, value) -> req.setValue(value, forHTTPHeaderField = name) }
        request.body?.let { req.setHTTPBody(it.encodeToByteArray().toNSData()) }
        return suspendCancellableCoroutine { cont ->
            val task = session.dataTaskWithRequest(req) { data, response, error ->
                when {
                    error != null -> cont.resumeWithException(AiConnectionException(error.localizedDescription))
                    response !is NSHTTPURLResponse -> cont.resumeWithException(AiConnectionException("no HTTP response"))
                    else -> cont.resume(AiHttpResponse(response.statusCode.toInt(), data?.toText() ?: ""))
                }
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    private companion object {
        /** Five Tavily pages with their text stay far below this. */
        const val MAX_BYTES = 8 * 1024 * 1024
    }

    private fun NSData.toText(): String {
        val size = minOf(length.toLong(), MAX_BYTES.toLong()).toInt()
        if (size == 0) return ""
        return bytes?.readBytes(size)?.decodeToString() ?: ""
    }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/**
 * The user's API keys in the iOS Keychain (generic passwords under the app's own service name), readable only on this
 * device after its first unlock: kept out of iCloud Keychain and of backups restored to another device, as the
 * Android version keeps them out of every backup.
 */
class IosSecretStore(private val service: String = "com.coffeejournal.ai") : SecretStore {
    override val supported: Boolean get() = true

    override suspend fun get(name: String): String? = withContext(Dispatchers.Default) {
        keychainQuery(name, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                when (val status = SecItemCopyMatching(query, result.ptr)) {
                    errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.let { data ->
                        data.bytes?.readBytes(data.length.toInt())?.decodeToString()
                    }
                    errSecItemNotFound -> null
                    else -> throw IllegalStateException("Keychain read failed ($status)")
                }
            }
        }
    }

    override suspend fun put(name: String, value: String) = withContext(Dispatchers.Default) {
        deleteItem(name)
        val data = CFBridgingRetain(value.encodeToByteArray().toNSData())
        try {
            keychainQuery(name, kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) { query ->
                val status = SecItemAdd(query, null)
                if (status != errSecSuccess) throw IllegalStateException("Keychain write failed ($status)")
            }
        } finally {
            CFRelease(data)
        }
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.Default) { deleteItem(name) }

    private fun deleteItem(name: String) {
        keychainQuery(name) { query ->
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) throw IllegalStateException("Keychain delete failed ($status)")
        }
    }

    /** The item's query (class, service, account) plus [extra], released after [block]. */
    private fun <T> keychainQuery(name: String, vararg extra: Pair<CFStringRef?, CFTypeRef?>, block: (CFMutableDictionaryRef?) -> T): T {
        val query = CFDictionaryCreateMutable(null, (3 + extra.size).toLong(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = CFBridgingRetain(NSString.create(string = name))
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            extra.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
            return block(query)
        } finally {
            CFRelease(serviceRef)
            CFRelease(accountRef)
            CFRelease(query)
        }
    }
}
