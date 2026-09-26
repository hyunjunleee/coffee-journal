package com.coffeejournal.domain.rules

import kotlin.random.Random

/** Web-compatible ids: base36 timestamp followed by 5 random base36 characters. */
object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun newId(now: Long = Dates.nowMillis()): String = now.toString(36) + randomBase36(5)

    fun newCustomId(prefix: String, now: Long = Dates.nowMillis()): String = prefix + now.toString(36) + randomBase36(4)

    private fun randomBase36(n: Int): String = buildString { repeat(n) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) } }
}
