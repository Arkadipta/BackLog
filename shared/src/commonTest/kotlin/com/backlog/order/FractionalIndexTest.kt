package com.backlog.order

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class FractionalIndexTest {
    private fun between(a: String?, b: String?) = FractionalIndex.keyBetween(a, b)

    /** Vectors from the `fractional-indexing` package's own test suite. */
    @Test
    fun matchesTheReferenceImplementation() {
        val cases = listOf(
            Triple(null, null, "a0"),
            Triple(null, "a0", "Zz"),
            Triple(null, "Zz", "Zy"),
            Triple("a0", null, "a1"),
            Triple("a1", null, "a2"),
            Triple("a0", "a1", "a0V"),
            Triple("a1", "a2", "a1V"),
            Triple("a0V", "a1", "a0l"),
            Triple("Zz", "a0", "ZzV"),
            Triple("Zz", "a1", "a0"),
            Triple(null, "Y00", "Xzzz"),
            Triple("bzz", null, "c000"),
            Triple("a0", "a0V", "a0G"),
            Triple("a0", "a0G", "a08"),
            Triple("b125", "b129", "b127"),
            Triple("a0", "a1V", "a1"),
            Triple("Zz", "a01", "a0"),
            Triple(null, "a0V", "a0"),
            Triple(null, "b999", "b99"),
            Triple(null, "A000000000000000000000000001", "A000000000000000000000000000V"),
            Triple("zzzzzzzzzzzzzzzzzzzzzzzzzzy", null, "zzzzzzzzzzzzzzzzzzzzzzzzzzz"),
            Triple("zzzzzzzzzzzzzzzzzzzzzzzzzzz", null, "zzzzzzzzzzzzzzzzzzzzzzzzzzzV"),
        )
        for ((a, b, expected) in cases) assertEquals(expected, between(a, b), "between($a, $b)")
    }

    @Test
    fun rejectsInvalidInput() {
        val cases = listOf(
            null to "A00000000000000000000000000",
            "a00" to null,
            "a00" to "a1",
            "0" to "1",
            "a1" to "a0",
            "a1" to "a1",
            "" to null,
            "a" to null,
            "a0!" to null,
        )
        for ((a, b) in cases) {
            assertFailsWith<IllegalArgumentException>("between($a, $b)") { between(a, b) }
        }
    }

    @Test
    fun nKeysMatchTheReferenceImplementation() {
        assertEquals("a0 a1 a2 a3 a4", FractionalIndex.keysBetween(null, null, 5).joinToString(" "))
        assertEquals(
            "a5 a6 a7 a8 a9 aA aB aC aD aE",
            FractionalIndex.keysBetween("a4", null, 10).joinToString(" "),
        )
        assertEquals("Zv Zw Zx Zy Zz", FractionalIndex.keysBetween(null, "a0", 5).joinToString(" "))
        assertEquals(
            "a04 a08 a0G a0K a0O a0V a0Z a0d a0l a0t a1 a14 a18 a1G a1O a1V a1Z a1d a1l a1t",
            FractionalIndex.keysBetween("a0", "a2", 20).joinToString(" "),
        )
        assertEquals(emptyList(), FractionalIndex.keysBetween("a0", "a1", 0))
    }

    @Test
    fun nKeysFallStrictlyBetweenAndInOrder() {
        val rnd = Random(3)
        val bounds = listOf<Pair<String?, String?>>(
            null to null, "a0" to null, null to "a0", "a0" to "a1", "a0" to "a0V", "Zz" to "a01", "a0zzz" to "a1",
        )
        for ((a, b) in bounds) {
            val n = rnd.nextInt(1, 200)
            val keys = FractionalIndex.keysBetween(a, b, n)
            assertEquals(n, keys.size)
            val all = listOfNotNull(a) + keys + listOfNotNull(b)
            assertStrictlySorted(all)
            keys.forEach { assertTrue(FractionalIndex.isValidKey(it), it) }
        }
    }

    @Test
    fun aThousandRandomInsertsStaySorted() {
        for (seed in 0 until 5) {
            val rnd = Random(seed)
            val keys = mutableListOf<String>()
            repeat(1_000) {
                val at = rnd.nextInt(keys.size + 1)
                val before = keys.getOrNull(at - 1)
                val after = keys.getOrNull(at)
                val key = between(before, after)
                if (before != null) assertTrue(before < key, "$before !< $key")
                if (after != null) assertTrue(key < after, "$key !< $after")
                assertTrue(FractionalIndex.isValidKey(key), key)
                keys.add(at, key)
            }
            assertStrictlySorted(keys)
            assertEquals(keys, keys.sorted())
        }
    }

    @Test
    fun repeatedInsertsAtOneSpotStayShort() {
        // Appending, prepending and always inserting just after the first item
        // are the common UI moves. Keys must stay valid and grow slowly.
        var last: String? = null
        repeat(10_000) { last = between(last, null) }
        assertTrue(last!!.length <= 4, last)

        var first: String? = null
        repeat(10_000) { first = between(null, first) }
        assertTrue(first!!.length <= 4, first)

        val head = between(null, null)
        var next = between(head, null)
        repeat(1_000) {
            val k = between(head, next)
            assertTrue(head < k && k < next)
            next = k
        }
        // Each step at worst adds one digit per ~6 halvings of the gap.
        assertTrue(next.length < 200, "length ${next.length}")
    }

    private fun assertStrictlySorted(keys: List<String>) {
        for (i in 1 until keys.size) {
            if (keys[i - 1] >= keys[i]) fail("${keys[i - 1]} !< ${keys[i]} at $i")
        }
    }
}
