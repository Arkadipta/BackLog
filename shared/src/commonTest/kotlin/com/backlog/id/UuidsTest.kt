package com.backlog.id

import com.backlog.time.TestClock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidsTest {
    private val uuidShape = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    // RFC 9562 Appendix A.6: timestamp 0x017F22E279B0 (2022-02-22 19:22:22 UTC).
    private val rfcV7Millis = 0x017F22E279B0L

    @Test
    fun v7HasVersionAndVariantBits() {
        val gen = UuidV7Generator(TestClock(rfcV7Millis))
        repeat(1_000) {
            val id = gen.next()
            assertTrue(uuidShape.matches(id), id)
            assertEquals('7', id[14], id)
            assertTrue(id[19] in "89ab", id)
            assertEquals(7, uuidVersion(id))
            assertEquals(0b10, uuidVariant(id))
        }
    }

    @Test
    fun v7EncodesTheClockTime() {
        val id = UuidV7Generator(TestClock(rfcV7Millis)).next()
        // Same timestamp prefix as the RFC's example 017f22e2-79b0-7cc3-98c4-dc0c0c07398f.
        assertTrue(id.startsWith("017f22e2-79b0-7"), id)
        assertEquals(rfcV7Millis, uuidV7Timestamp(id))
        assertEquals(rfcV7Millis, uuidV7Timestamp("017F22E2-79B0-7CC3-98C4-DC0C0C07398F"))
    }

    @Test
    fun v7SortsInTimeOrder() {
        val clock = TestClock(1_700_000_000_000)
        val gen = UuidV7Generator(clock, Random(7))
        val ids = mutableListOf<String>()
        val rnd = Random(42)
        repeat(5_000) {
            ids += gen.next()
            when (rnd.nextInt(4)) {
                0 -> {} // Same millisecond.
                1 -> clock.advanceMillis(1)
                2 -> clock.advanceMillis(rnd.nextLong(1, 100_000))
                3 -> clock.advanceMillis(-rnd.nextLong(1, 5_000)) // Clock corrected backwards.
            }
        }
        assertEquals(ids.size, ids.toSet().size, "duplicates")
        for (i in 1 until ids.size) assertTrue(ids[i - 1] < ids[i], "${ids[i - 1]} !< ${ids[i]}")
    }

    @Test
    fun v7FromLaterMillisecondsSortsLaterAcrossGenerators() {
        val a = UuidV7Generator(TestClock(1_000)).next()
        val b = UuidV7Generator(TestClock(1_001)).next()
        assertTrue(a < b)
        assertEquals(1_000, uuidV7Timestamp(a))
        assertEquals(1_001, uuidV7Timestamp(b))
    }

    @Test
    fun v7CounterOverflowCarriesIntoTheTimestamp() {
        val clock = TestClock(5_000)
        val gen = UuidV7Generator(clock)
        // The counter starts below 2048, so 4096 ids in one ms must carry.
        val ids = List(4_096) { gen.next() }
        for (i in 1 until ids.size) assertTrue(ids[i - 1] < ids[i])
        assertEquals(5_001, uuidV7Timestamp(ids.last()))
        assertEquals(7, uuidVersion(ids.last()))
    }

    @Test
    fun v5MatchesRfc9562Example() {
        // RFC 9562 Appendix A.4: DNS namespace, name "www.example.com".
        assertEquals(
            "2ed6657d-e927-568b-95e1-2665a8aea6a2",
            uuidV5("6ba7b810-9dad-11d1-80b4-00c04fd430c8", "www.example.com"),
        )
    }

    @Test
    fun v5IsStableAndVersioned() {
        // Pinned (checked against Python's uuid.uuid5). If this changes, every
        // device would seed a different Inbox (D5).
        val inbox = uuidV5(NS_BACKLOG, "inbox")
        assertEquals(INBOX_V5, inbox)
        assertEquals(5, uuidVersion(inbox))
        assertEquals(0b10, uuidVariant(inbox))
        assertNotEquals(inbox, uuidV5(NS_BACKLOG, "Inbox"))
        // Non-ASCII names hash their UTF-8 bytes.
        assertEquals(EMOJI_V5, uuidV5(NS_BACKLOG, "next:✅"))
    }

    @Test
    fun namespaceIsAVersion4Uuid() {
        assertEquals(4, uuidVersion(NS_BACKLOG))
        assertEquals(0b10, uuidVariant(NS_BACKLOG))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        val s = "017f22e2-79b0-7cc3-98c4-dc0c0c07398f"
        assertEquals(s, formatUuid(parseUuid(s)))
        assertEquals(s, formatUuid(parseUuid(s.uppercase())))
        assertFailsWith<IllegalArgumentException> { parseUuid("017f22e2-79b0-7cc3-98c4-dc0c0c07398") }
        assertFailsWith<IllegalArgumentException> { parseUuid("017f22e2-79b0-7cc3-98c4-dc0c0c07398g") }
        assertFailsWith<IllegalArgumentException> { parseUuid("017f22e2079b0-7cc3-98c4-dc0c0c07398f") }
    }

    private companion object {
        const val INBOX_V5 = "8494b556-8818-5209-99e5-3f3bce5d2b0f"
        const val EMOJI_V5 = "b8d3017a-b32f-5d52-98ad-6b72f5b3cfa5"
    }
}
