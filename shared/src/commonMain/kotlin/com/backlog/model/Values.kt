package com.backlog.model

/*
 * Value types stored as tokens or small integers. Each one decodes leniently:
 * a newer app version may sync a value this one doesn't know, and that must
 * never break reading a row. Swift sees these as real enums (SKIE).
 */

/** Stored in `task.priority` as 0..3. */
enum class Priority(val level: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    ;

    companion object {
        /** Out-of-range values clamp to the nearest priority. */
        fun fromLevel(level: Long): Priority = when {
            level <= 0 -> NONE
            level >= 3 -> HIGH
            else -> entries[level.toInt()]
        }
    }
}

/** Stored in `task.repeat_from`. */
enum class RepeatFrom(val token: String) {
    /** The next instance is due one interval after this one's due date (the default). */
    DUE("due"),

    /** The next instance is due one interval after this one was completed. */
    COMPLETION("completion"),
    ;

    companion object {
        /** Unknown tokens read as [DUE]. */
        fun fromToken(token: String): RepeatFrom = entries.firstOrNull { it.token == token } ?: DUE
    }
}

/**
 * The eight list colours (spec, Visual design). Sync stores the token, and each
 * platform maps it to its own light and dark values.
 */
enum class ListColor(val token: String) {
    SKY("sky"),
    MINT("mint"),
    AMBER("amber"),
    CORAL("coral"),
    VIOLET("violet"),
    SLATE("slate"),
    ROSE("rose"),
    TEAL("teal"),
    ;

    companion object {
        /** Unknown tokens read as [SKY], the column's default. */
        fun fromToken(token: String): ListColor = entries.firstOrNull { it.token == token } ?: SKY
    }
}
