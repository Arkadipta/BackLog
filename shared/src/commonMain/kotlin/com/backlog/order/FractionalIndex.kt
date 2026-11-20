package com.backlog.order

/**
 * Fractional index keys for `sort_key`, so moving an item rewrites one row.
 *
 * A port of the `fractional-indexing` npm package (rocicorp, CC0; checked
 * against 3.4.0's source), which implements David Greenspan's "Implementing
 * Fractional Indexing", with the default base-62 alphabet. Two deliberate
 * differences: `a > b` throws instead of being swapped, since reversed
 * neighbours mean a caller bug, and a character outside the alphabet is
 * rejected instead of being read as zero. A key is a
 * variable-length integer part, whose first character encodes its length,
 * followed by a base-62 fraction with no trailing zero. Keys compare by plain
 * string order, in Kotlin and in SQLite alike.
 *
 * Two devices inserting at the same spot can produce equal keys, so every
 * query orders by (`sort_key`, `id`) (D8).
 */
internal object FractionalIndex {
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val ZERO = '0'

    /** The smallest integer part, which may not appear without a fraction. */
    private val SMALLEST_INTEGER = "A" + "0".repeat(26)

    /**
     * A key strictly between [a] and [b]. A null [a] means "before [b]"
     * and a null [b] means "after [a]"; both null gives the first key.
     *
     * @throws IllegalArgumentException if a key is malformed or `a >= b`.
     */
    fun keyBetween(a: String?, b: String?): String {
        if (a != null) validateKey(a)
        if (b != null) validateKey(b)
        if (a != null && b != null) require(a < b) { "$a >= $b" }

        if (a == null) {
            if (b == null) return "a$ZERO"
            val ib = integerPart(b)
            val fb = b.substring(ib.length)
            if (ib == SMALLEST_INTEGER) return ib + midpoint("", fb)
            if (ib < b) return ib
            return requireNotNull(decrementInteger(ib)) { "cannot decrement any more" }
        }

        if (b == null) {
            val ia = integerPart(a)
            val fa = a.substring(ia.length)
            return incrementInteger(ia) ?: (ia + midpoint(fa, null))
        }

        val ia = integerPart(a)
        val fa = a.substring(ia.length)
        val ib = integerPart(b)
        val fb = b.substring(ib.length)
        if (ia == ib) return ia + midpoint(fa, fb)
        val i = requireNotNull(incrementInteger(ia)) { "cannot increment any more" }
        return if (i < b) i else ia + midpoint(fa, null)
    }

    /**
     * [n] keys in increasing order, all strictly between [a] and [b].
     * Splitting the gap evenly keeps the keys short.
     */
    fun keysBetween(a: String?, b: String?, n: Int): List<String> {
        require(n >= 0) { "n must not be negative: $n" }
        if (n == 0) return emptyList()
        if (n == 1) return listOf(keyBetween(a, b))
        if (b == null) {
            val out = ArrayList<String>(n)
            var c = keyBetween(a, null)
            out += c
            repeat(n - 1) {
                c = keyBetween(c, null)
                out += c
            }
            return out
        }
        if (a == null) {
            val out = ArrayList<String>(n)
            var c = keyBetween(null, b)
            out += c
            repeat(n - 1) {
                c = keyBetween(null, c)
                out += c
            }
            out.reverse()
            return out
        }
        val mid = n / 2
        val c = keyBetween(a, b)
        return keysBetween(a, c, mid) + c + keysBetween(c, b, n - mid - 1)
    }

    /** True if [key] is a well-formed key. */
    fun isValidKey(key: String): Boolean = runCatching { validateKey(key) }.isSuccess

    /**
     * A digit string strictly between fractions [a] and [b], where a null
     * [b] means the top of the range. Neither may end in a zero digit.
     */
    private fun midpoint(a: String, b: String?): String {
        if (b != null) require(a < b) { "$a >= $b" }
        require(a.lastOrNull() != ZERO && b?.lastOrNull() != ZERO) { "trailing zero" }
        if (!b.isNullOrEmpty()) {
            // Drop the longest common prefix, reading `a` as zero-padded.
            // `b` can't run out first, because `a < b`.
            var n = 0
            while ((a.getOrNull(n) ?: ZERO) == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.substring(n))
        }
        // The first digits (or a missing digit) differ.
        val digitA = if (a.isEmpty()) 0 else digit(a[0])
        val digitB = if (b == null) DIGITS.length else digit(b[0])
        return if (digitB - digitA > 1) {
            // Math.round(0.5 * (digitA + digitB)), as in the original.
            DIGITS[(digitA + digitB + 1) / 2].toString()
        } else if (b != null && b.length > 1) {
            // Consecutive first digits: b's first digit alone is between.
            b.substring(0, 1)
        } else {
            // `b` is null or a single digit, and a's first digit is just
            // below it: keep that digit and go one place deeper.
            DIGITS[digitA] + midpoint(a.drop(1), null)
        }
    }

    private fun digit(c: Char): Int {
        val d = DIGITS.indexOf(c)
        require(d >= 0) { "invalid digit: $c" }
        return d
    }

    private fun integerLength(head: Char): Int = when (head) {
        in 'a'..'z' -> head - 'a' + 2
        in 'A'..'Z' -> 'Z' - head + 2
        else -> throw IllegalArgumentException("invalid order key head: $head")
    }

    private fun integerPart(key: String): String {
        require(key.isNotEmpty()) { "empty order key" }
        val length = integerLength(key[0])
        require(length <= key.length) { "invalid order key: $key" }
        return key.substring(0, length)
    }

    private fun validateKey(key: String) {
        require(key != SMALLEST_INTEGER) { "invalid order key: $key" }
        val i = integerPart(key)
        for (c in key.substring(1)) digit(c)
        require(key.substring(i.length).lastOrNull() != ZERO) { "invalid order key: $key" }
    }

    private fun validateInteger(int: String) {
        require(int.length == integerLength(int[0])) { "invalid integer part of order key: $int" }
    }

    /** The next integer part, or null past the largest one. */
    private fun incrementInteger(x: String): String? {
        validateInteger(x)
        val head = x[0]
        val digs = x.substring(1).toCharArray().toMutableList()
        var carry = true
        var i = digs.size - 1
        while (carry && i >= 0) {
            val d = digit(digs[i]) + 1
            if (d == DIGITS.length) {
                digs[i] = ZERO
            } else {
                digs[i] = DIGITS[d]
                carry = false
            }
            i--
        }
        if (!carry) return head + digs.joinToString("")
        if (head == 'Z') return "a$ZERO"
        if (head == 'z') return null
        val h = head + 1
        if (h > 'a') digs += ZERO else digs.removeAt(digs.size - 1)
        return h + digs.joinToString("")
    }

    /** The previous integer part, or null below the smallest one. */
    private fun decrementInteger(x: String): String? {
        validateInteger(x)
        val head = x[0]
        val digs = x.substring(1).toCharArray().toMutableList()
        var borrow = true
        var i = digs.size - 1
        while (borrow && i >= 0) {
            val d = digit(digs[i]) - 1
            if (d == -1) {
                digs[i] = DIGITS.last()
            } else {
                digs[i] = DIGITS[d]
                borrow = false
            }
            i--
        }
        if (!borrow) return head + digs.joinToString("")
        if (head == 'a') return "Z" + DIGITS.last()
        if (head == 'A') return null
        val h = head - 1
        if (h < 'Z') digs += DIGITS.last() else digs.removeAt(digs.size - 1)
        return h + digs.joinToString("")
    }
}
