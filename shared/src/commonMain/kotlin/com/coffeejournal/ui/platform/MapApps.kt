package com.coffeejournal.ui.platform

/**
 * Opens [uri] in the app that handles it (an `nmap://` link in Naver Map, a map.kakao.com link in Kakao Map or the
 * browser). When no app on the device can open it, [fallbackUrl] (a web page) opens instead, if given.
 */
expect fun openMapUri(uri: String, fallbackUrl: String?)
