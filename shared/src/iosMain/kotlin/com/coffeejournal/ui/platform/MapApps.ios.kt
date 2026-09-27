package com.coffeejournal.ui.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/*
 * canOpenURL answers true for an nmap:// link only when Naver Map is installed and the scheme is listed under
 * LSApplicationQueriesSchemes in iosApp/iosApp/Info.plist.
 */
actual fun openMapUri(uri: String, fallbackUrl: String?) {
    val app = UIApplication.sharedApplication
    val primary = NSURL.URLWithString(uri)
    if (primary != null && app.canOpenURL(primary)) {
        // openURL(_:) without options is refused ("Force returning false") since iOS 18
        app.openURL(primary, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return
    }
    val web = fallbackUrl?.let { NSURL.URLWithString(it) } ?: return
    app.openURL(web, options = emptyMap<Any?, Any?>(), completionHandler = null)
}
