package com.coffeejournal.domain.rules

object RoastFamily {
    const val LIGHT = "light"
    const val MEDIUM = "medium"
    const val DARK = "dark"

    /** Web roastFamily: dark is tested first so "미디엄 다크" is dark. */
    fun of(value: String?): String {
        val v = (value ?: "").lowercase()
        return when {
            Regex("dark|다크|강배전").containsMatchIn(v) -> DARK
            Regex("light|라이트|약배전").containsMatchIn(v) -> LIGHT
            Regex("medium|미디엄|중배전|중간").containsMatchIn(v) -> MEDIUM
            else -> ""
        }
    }

    fun label(family: String): String = when (family) {
        LIGHT -> "라이트계"; MEDIUM -> "중간"; DARK -> "다크계"; else -> ""
    }
}
