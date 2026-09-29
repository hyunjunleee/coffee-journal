package com.coffeejournal.domain.rules

import com.coffeejournal.domain.reference.NoteSynonyms

object NoteCanon {
    /** Web canonicalNoteLabel: trim, drop trailing punctuation, map synonyms by whitespace-free key. */
    fun canonical(raw: String?): String {
        var s = (raw ?: "").trim().trimEnd('.', '。', ',', '、').trim()
        if (s.isEmpty()) return ""
        val key = s.replace(Regex("\\s+"), "")
        NoteSynonyms.map[key]?.let { return it }
        NoteSynonyms.map[key.lowercase()]?.let { return it }
        return s
    }

    /** Web splitNoteList: split on commas, semicolons and newlines then canonicalise. */
    fun split(text: String?): List<String> =
        (text ?: "").split(Regex("[,;\\n]")).map { canonical(it) }.filter { it.isNotEmpty() }

    /** Aggregation key for the note cloud. */
    fun key(label: String): String = label.lowercase().replace(Regex("\\s+"), "")

    /** Chip helpers used by forms: ", " joined storage form. */
    fun parseChips(stored: String?): List<String> =
        (stored ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun joinChips(chips: List<String>): String = chips.joinToString(", ")

    /**
     * [existing] and the comma-separated notes in [input], each once; typed English starts each word with a capital
     * ([EnglishCase]: "earl grey" → "Earl Grey"), as the other fields do, also for a note still in the box at saving.
     */
    fun addChips(existing: List<String>, input: String): List<String> {
        val seen = LinkedHashSet(existing)
        input.split(',').map { EnglishCase.words(it.trim()) }.filter { it.isNotEmpty() }.forEach { seen += it }
        return seen.toList()
    }
}
