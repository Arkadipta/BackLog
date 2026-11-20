package com.backlog.id

import com.backlog.time.Clock
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random

/**
 * The namespace for every name-based id BackLog derives (D5, D21), such as the
 * Inbox's `uuidV5(NS_BACKLOG, "inbox")`. Created once in task 1.1.
 * Never change it: every derived id on every device depends on it.
 */
internal const val NS_BACKLOG: String = "457aad8b-b487-40e0-bafc-670f5c162284"

/**
 * Generates UUIDv7 strings (RFC 9562 §5.7) that are strictly increasing per
 * generator, even within one millisecond or when the wall clock goes backwards.
 *
 * `rand_a` (12 bits) holds a counter (RFC 9562 §6.2, method 1). Each new
 * millisecond seeds it with a random value below 2048, leaving at least 2048
 * ids of headroom. If the counter overflows, the timestamp moves 1 ms ahead
 * of the wall clock. `rand_b` (62 bits) is random.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class UuidV7Generator(
    private val clock: Clock,
    private val random: Random = CryptographyRandom,
) {
    // (unix ms << 12) | counter, of the last id handed out.
    private val last = AtomicLong(-1L)

    fun next(): String {
        while (true) {
            val prev = last.load()
            val now = clock.nowMillis()
            require(now in 0..MAX_TIMESTAMP) { "clock out of UUIDv7 range: $now" }
            val prevMs = if (prev < 0) -1L else prev ushr 12
            val state = if (now > prevMs) {
                (now shl 12) or random.nextInt(0, 2048).toLong()
            } else {
                // Same or earlier millisecond: count up from the last id.
                // Overflowing the 12-bit counter carries into the timestamp.
                prev + 1
            }
            if (last.compareAndSet(prev, state)) return format(state)
        }
    }

    private fun format(state: Long): String {
        val ms = state ushr 12
        val counter = (state and 0xFFF).toInt()
        val bytes = ByteArray(16)
        for (i in 0 until 6) bytes[i] = (ms ushr (8 * (5 - i))).toByte()
        bytes[6] = (0x70 or (counter ushr 8)).toByte()
        bytes[7] = counter.toByte()
        random.nextBytes(bytes, 8, 16)
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return formatUuid(bytes)
    }

    companion object {
        /** The largest 48-bit timestamp. */
        const val MAX_TIMESTAMP: Long = (1L shl 48) - 1
    }
}

/**
 * A name-based UUIDv5 (RFC 9562 §5.5): SHA-1 over the namespace's 16 bytes
 * followed by the name in UTF-8. Two devices derive the same id from the same
 * inputs, which is how D5 and D21 avoid duplicate rows.
 */
internal fun uuidV5(namespace: String, name: String): String {
    val input = parseUuid(namespace) + name.encodeToByteArray()
    val hash = sha1.hashBlocking(input)
    val bytes = hash.copyOf(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    return formatUuid(bytes)
}

// UUIDv5 is defined over SHA-1. It derives ids, not secrets, so SHA-1's
// collision weakness doesn't matter here.
@OptIn(DelicateCryptographyApi::class)
private val sha1 by lazy { CryptographyProvider.Default.get(SHA1).hasher() }

/** The version nibble of a UUID string, 0–15. */
internal fun uuidVersion(uuid: String): Int = (parseUuid(uuid)[6].toInt() ushr 4) and 0xF

/** The two variant bits of a UUID string; RFC 9562 UUIDs have `0b10`. */
internal fun uuidVariant(uuid: String): Int = (parseUuid(uuid)[8].toInt() ushr 6) and 0x3

/** The 48-bit Unix millisecond timestamp in a UUIDv7. */
internal fun uuidV7Timestamp(uuid: String): Long {
    val bytes = parseUuid(uuid)
    require(((bytes[6].toInt() ushr 4) and 0xF) == 7) { "not a UUIDv7: $uuid" }
    var ms = 0L
    for (i in 0 until 6) ms = (ms shl 8) or (bytes[i].toLong() and 0xFF)
    return ms
}

private const val HEX = "0123456789abcdef"

/** Lower-case `8-4-4-4-12` form. */
internal fun formatUuid(bytes: ByteArray): String {
    require(bytes.size == 16) { "a UUID has 16 bytes, not ${bytes.size}" }
    val out = StringBuilder(36)
    for (i in 0 until 16) {
        if (i == 4 || i == 6 || i == 8 || i == 10) out.append('-')
        val b = bytes[i].toInt() and 0xFF
        out.append(HEX[b ushr 4]).append(HEX[b and 0xF])
    }
    return out.toString()
}

/** Parses the `8-4-4-4-12` form, in either case. */
internal fun parseUuid(uuid: String): ByteArray {
    require(uuid.length == 36 && uuid[8] == '-' && uuid[13] == '-' && uuid[18] == '-' && uuid[23] == '-') {
        "not a UUID: $uuid"
    }
    val hex = uuid.replace("-", "")
    return ByteArray(16) { i ->
        val hi = hex[2 * i].digitToIntOrNull(16)
        val lo = hex[2 * i + 1].digitToIntOrNull(16)
        require(hi != null && lo != null) { "not a UUID: $uuid" }
        ((hi shl 4) or lo).toByte()
    }
}
