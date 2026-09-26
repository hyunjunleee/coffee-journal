package com.coffeejournal.domain.rules

/**
 * Number parsing for user text and backup data. Kotlin's toDoubleOrNull() accepts "NaN" and "Infinity" and turns
 * "1e999" into Infinity; roundToInt() then throws on NaN. Everything here treats such values as missing, the way the
 * web's `parseFloat(x) || 0` and `!(x > 0)` guards do.
 */
object Numbers {
    /** The finite number in [text] (trimmed), or null when it is blank, malformed, NaN or infinite. */
    fun parse(text: String?): Double? = text?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** [value] when it is finite, otherwise null. */
    fun finite(value: Double?): Double? = value?.takeIf { it.isFinite() }
}
