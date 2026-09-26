package com.coffeejournal.ui.bean

/**
 * Name order of the web's `a.localeCompare(b, 'ko')`: spaces and punctuation, then digits, then Hangul, then Hanja,
 * then Latin and other letters, comparing letters without case first ("apple" < "Banana"); at an otherwise equal
 * text the lower-case spelling comes first. Plain String order puts every Latin name before the Hangul ones and
 * upper case before lower case.
 */
internal object KoreanOrder : Comparator<String> {
    private fun group(c: Char): Int = when {
        c in '가'..'힣' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏' -> 2 // Hangul syllables and jamo
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' -> 3 // Hanja
        c.isDigit() -> 1
        c.isLetter() -> 4
        else -> 0
    }

    override fun compare(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        for (i in 0 until n) {
            val ca = a[i]
            val cb = b[i]
            if (ca == cb) continue
            val ga = group(ca)
            val gb = group(cb)
            if (ga != gb) return ga.compareTo(gb)
            val la = ca.lowercaseChar()
            val lb = cb.lowercaseChar()
            if (la != lb) return la.compareTo(lb)
        }
        if (a.length != b.length) return a.length.compareTo(b.length)
        for (i in 0 until n) {
            if (a[i] != b[i]) return if (a[i].isLowerCase()) -1 else 1
        }
        return 0
    }
}
