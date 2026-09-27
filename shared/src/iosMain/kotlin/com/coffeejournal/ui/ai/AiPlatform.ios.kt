package com.coffeejournal.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

/* Not compiled in this repository's Linux CI; verify on macOS when enabling the iOS target. */

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfc(text: String): String = (text as NSString).precomposedStringWithCanonicalMapping

/** Search Suggestions need a web view; the helper does not run on iOS yet, so there is nothing to show. */
@Composable
actual fun SearchSuggestions(html: String, modifier: Modifier) {
}

/** The AI helper is not connected on iOS yet: the screens say so instead of calling a service. */
class IosAiHttp : AiHttp {
    override val supported: Boolean get() = false
    override suspend fun send(request: AiHttpRequest): AiHttpResponse {
        throw AiConnectionException("not supported on iOS yet")
    }
}

/** Keys would go to the Keychain; until then nothing is stored and the settings say it is not supported yet. */
class IosSecretStore : SecretStore {
    override val supported: Boolean get() = false
    override suspend fun get(name: String): String? = null
    override suspend fun put(name: String, value: String) {
        throw UnsupportedOperationException("not supported on iOS yet")
    }
    override suspend fun delete(name: String) {}
}
