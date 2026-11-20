package com.backlog.sync

import com.backlog.time.TestClock
import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlcTest {
    private val wall = 1_763_600_000_000L

    @Test
    fun tickFollowsTheWallClock() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        assertEquals(Hlc(wall, 0, "devA"), hlc.tick())
        assertEquals(Hlc(wall, 1, "devA"), hlc.tick())
        clock.advanceMillis(5)
        assertEquals(Hlc(wall + 5, 0, "devA"), hlc.tick())
    }

    @Test
    fun staysMonotonicWhenTheWallClockGoesBackwards() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        val first = hlc.tick()
        clock.set(wall - 60_000)
        val second = hlc.tick()
        val third = hlc.tick()
        assertEquals(Hlc(wall, 1, "devA"), second)
        assertEquals(Hlc(wall, 2, "devA"), third)
        assertTrue(first < second && second < third)
        // Once the wall clock passes the old high-water mark, the counter resets.
        clock.set(wall + 1)
        assertEquals(Hlc(wall + 1, 0, "devA"), hlc.tick())
    }

    @Test
    fun staysMonotonicUnderARandomWalkOfTheWallClock() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        val rnd = Random(1)
        var prev = hlc.tick()
        repeat(20_000) {
            clock.advanceMillis(rnd.nextLong(-1_000, 1_000))
            val next = if (rnd.nextInt(5) == 0) {
                hlc.receive(Hlc(clock.nowMillis() + rnd.nextLong(-2_000, 2_000), rnd.nextInt(0, 0x10000), "devB"))
            } else {
                hlc.tick()
            }
            assertTrue(next > prev, "$next !> $prev")
            prev = next
        }
    }

    @Test
    fun receivingARemoteClockThatIsAhead() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        hlc.tick()
        val remote = Hlc(wall + 30_000, 7, "devB")
        val received = hlc.receive(remote)
        assertEquals(Hlc(wall + 30_000, 8, "devA"), received)
        assertTrue(received > remote)
        // Local edits now stamp above the remote one, though the wall clock is behind it.
        val local = hlc.tick()
        assertEquals(Hlc(wall + 30_000, 9, "devA"), local)
        assertTrue(local > remote)
        clock.set(wall + 30_001)
        assertEquals(Hlc(wall + 30_001, 0, "devA"), hlc.tick())
    }

    @Test
    fun receivingWithEqualWallTimesTakesTheHigherCounter() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        hlc.tick() // (wall, 0)
        assertEquals(Hlc(wall, 6, "devA"), hlc.receive(Hlc(wall, 5, "devB")))
        assertEquals(Hlc(wall, 7, "devA"), hlc.receive(Hlc(wall, 2, "devB")))
    }

    @Test
    fun receivingAnOlderClockStillTicks() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        hlc.tick()
        assertEquals(Hlc(wall, 1, "devA"), hlc.receive(Hlc(wall - 10_000, 3, "devB")))
        clock.advanceMillis(1)
        assertEquals(Hlc(wall + 1, 0, "devA"), hlc.receive(Hlc(wall - 10_000, 3, "devB")))
    }

    @Test
    fun counterOverflowMovesTheWallTimeAhead() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        // Frozen wall clock: 0x10000 stamps fill the counter, the next carries.
        var last = hlc.tick()
        repeat(Hlc.MAX_COUNTER) { last = hlc.tick() }
        assertEquals(Hlc(wall, 0xFFFF, "devA"), last)
        assertEquals("ffff", last.encode().substring(14, 18))
        val carried = hlc.tick()
        assertEquals(Hlc(wall + 1, 0, "devA"), carried)
        assertTrue(carried > last)
        assertTrue(carried.encode() > last.encode())
    }

    @Test
    fun counterOverflowOnReceive() {
        val clock = TestClock(wall)
        val hlc = HlcClock("devA", clock)
        assertEquals(Hlc(wall + 1, 0, "devA"), hlc.receive(Hlc(wall, 0xFFFF, "devB")))
    }

    @Test
    fun restoresFromTheLastStamp() {
        val clock = TestClock(wall - 5_000)
        val hlc = HlcClock("devA", clock, last = Hlc(wall, 3, "devB"))
        assertEquals(Hlc(wall, 3, "devA"), hlc.current())
        assertEquals(Hlc(wall, 4, "devA"), hlc.tick())
    }

    @Test
    fun encodesInTheD11Format() {
        assertEquals("1763600000000-0000-devA", Hlc(wall, 0, "devA").encode())
        assertEquals("0000000000042-00ff-x", Hlc(42, 255, "x").encode())
        assertEquals("9999999999999-ffff-d", Hlc(Hlc.MAX_WALL, 0xFFFF, "d").encode())
        assertEquals("0000000000000-0000-0", Hlc.ZERO.encode())
        val device = "019a9f1c-2b3d-7e4f-8a1b-2c3d4e5f6a7b"
        assertEquals(Hlc(wall, 10, device), Hlc.decode("1763600000000-000a-$device"))
    }

    @Test
    fun rejectsMalformedStrings() {
        for (bad in listOf(
            "",
            "1763600000000-0000-",
            "176360000000-0000-devA",
            "1763600000000-000-devA",
            "1763600000000-00FF-devA",
            "1763600000000_0000-devA",
            "1763600000000-0000_devA",
            "1763600000000-0000-dev A",
            "-763600000000-0000-devA",
            "1763600000000-0000-dév",
        )) {
            assertNull(Hlc.decodeOrNull(bad), bad)
        }
        assertFailsWith<IllegalArgumentException> { Hlc.decode("nope") }
        assertFailsWith<IllegalArgumentException> { Hlc(-1, 0, "a") }
        assertFailsWith<IllegalArgumentException> { Hlc(0, 0x10000, "a") }
        assertFailsWith<IllegalArgumentException> { Hlc(Hlc.MAX_WALL + 1, 0, "a") }
        assertFailsWith<IllegalArgumentException> { HlcClock("", TestClock()) }
    }

    @Test
    fun zeroLosesToAnyRealStamp() {
        assertTrue(Hlc.ZERO < Hlc(1, 0, "0"))
        assertTrue(Hlc.ZERO < HlcClock("devA", TestClock(wall)).tick())
    }

    @Test
    fun equalWallAndCounterAreDecidedByDeviceId() {
        val a = Hlc(wall, 3, "devA")
        val b = Hlc(wall, 3, "devB")
        assertTrue(a < b)
        assertTrue(a.encode() < b.encode())
    }

    /** Property: string order equals clock order, and decode inverts encode. */
    @Test
    fun stringOrderMatchesClockOrder() {
        val rnd = Random(2026)
        // A few device ids, some prefixes of others, so ties and prefix cases occur.
        val devices = listOf("a", "ab", "b", "A", "Z", "0", "9", "-", "_", "devA", "devA-2", "019a9f1c-2b3d-7e4f")
        fun randomWall() = when (rnd.nextInt(4)) {
            0 -> rnd.nextLong(0, 1_000)
            1 -> wall + rnd.nextLong(-3, 3) // Close together, so counters decide.
            2 -> rnd.nextLong(0, Hlc.MAX_WALL + 1)
            else -> Hlc.MAX_WALL - rnd.nextLong(0, 3)
        }
        fun randomCounter() = when (rnd.nextInt(3)) {
            0 -> rnd.nextInt(0, 3)
            1 -> rnd.nextInt(0x0F, 0x12) // Crosses a hex digit boundary.
            else -> rnd.nextInt(0, Hlc.MAX_COUNTER + 1)
        }
        fun randomHlc() = Hlc(randomWall(), randomCounter(), devices.random(rnd))

        repeat(50_000) {
            val x = randomHlc()
            val y = if (rnd.nextInt(4) == 0) x.copy(deviceId = devices.random(rnd)) else randomHlc()
            assertEquals(
                x.compareTo(y).sign,
                x.encode().compareTo(y.encode()).sign,
                "$x vs $y",
            )
            assertEquals(x, Hlc.decode(x.encode()))
        }

        val sample = List(2_000) { randomHlc() }
        assertEquals(sample.sorted().map { it.encode() }, sample.map { it.encode() }.sorted())
    }
}
