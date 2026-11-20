package com.backlog.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * What the apps read. These are the shared core's own types, not SQLDelight's
 * generated rows, which are hidden from Swift (shared/build.gradle.kts).
 *
 * Names: `Task` and `List` would shadow `_Concurrency.Task` and `SwiftUI.List`
 * in the Mac app, hence `TaskItem` and `TaskList`. Ids are plain UUID strings,
 * because Swift erases value classes to `Any` (0.2 notes). Tombstoned rows
 * never become models.
 */

/** A list, including the Inbox. */
data class TaskList(
    val id: String,
    val name: String,
    val color: ListColor,
    /** An SF Symbol or Material icon name. */
    val icon: String?,
    /** Fractional index; ties go by [id] (D8). */
    val sortKey: String,
    val isInbox: Boolean,
)

/** A task or a sub-task (a task with a [parentId]). */
data class TaskItem(
    val id: String,
    val listId: String,
    /** The parent task, for a sub-task. Sub-tasks nest one level only. */
    val parentId: String?,
    val title: String,
    /** Markdown source. Top-level tasks only. */
    val notesMd: String?,
    val priority: Priority,
    /** A floating local date (D6). */
    val dueDate: LocalDate?,
    /** A floating wall time on [dueDate]; null means all-day (D6). */
    val dueTime: LocalTime?,
    val reminder: Reminder?,
    /** An RFC 5545 RRULE in the supported subset (1.6). */
    val rrule: String?,
    val repeatFrom: RepeatFrom,
    /** The id of the series' first instance (D21). */
    val seriesId: String?,
    /** UTC epoch ms; null while open. Swift reads it as `KotlinLong?`, so prefer [isCompleted]. */
    val completedAt: Long?,
    /** Order among siblings; ties go by [id] (D8). */
    val sortKey: String,
) {
    val isCompleted: Boolean get() = completedAt != null
    val isSubtask: Boolean get() = parentId != null
}

/**
 * A task's single reminder. [atMillis] is always the instant it fires.
 * [timeZone] is the zone it was set in, which the planner (1.9) uses to keep
 * its wall time when the device changes zone (D6); null means treat
 * [atMillis] as a fixed instant.
 */
sealed interface Reminder {
    val atMillis: Long
    val timeZone: String?

    /** A fixed time that stays put when the task is rescheduled (D7). */
    data class Absolute(override val atMillis: Long, override val timeZone: String?) : Reminder

    /**
     * [minutes] before the due time, or before 09:00 for an all-day task. It
     * follows the task when the due date or time changes (D7).
     */
    data class BeforeDue(val minutes: Int, override val atMillis: Long, override val timeZone: String?) : Reminder
}
