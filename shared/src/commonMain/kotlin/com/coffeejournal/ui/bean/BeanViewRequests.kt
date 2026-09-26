package com.coffeejournal.ui.bean

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide request for the sub view the 원두 tab should show next (Koin single). Another screen sets it before it
 * switches to the tab — the calendar's blend row asks for 블렌드 (design §2.3 #5) — and the tab takes it once.
 * A holder rather than a route argument, because switching tabs restores the saved 원두 entry with its old arguments.
 */
class BeanViewRequests {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(view: String) {
        if (view in BeanViews.all) _pending.value = view
    }

    /** Called by the tab once it shows the requested view. */
    fun clear() {
        _pending.value = null
    }
}
