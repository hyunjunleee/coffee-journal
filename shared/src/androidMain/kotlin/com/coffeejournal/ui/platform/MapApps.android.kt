package com.coffeejournal.ui.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * Starts the implicit VIEW intent directly: package visibility (Android 11+) would hide Naver Map from a
 * queryIntentActivities check without a <queries> entry, while starting the intent works and fails with
 * ActivityNotFoundException when nothing handles it, which is when the web page opens instead.
 */
actual fun openMapUri(uri: String, fallbackUrl: String?) {
    val ctx = appContextForUrl ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        if (fallbackUrl != null) runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: SecurityException) {
        // an app that exports no activity for us: same as not installed
        if (fallbackUrl != null) runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
