package com.backlog.db

import com.backlog.model.ListColor
import com.backlog.model.Priority
import com.backlog.model.Reminder
import com.backlog.model.RepeatFrom
import com.backlog.model.TaskItem
import com.backlog.model.TaskList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.backlog.db.sql.List as ListRow
import com.backlog.db.sql.Task as TaskRow

/*
 * Generated rows to models. Decoding is lenient, because a row can hold
 * anything another device synced: a value this version can't read becomes
 * the field's empty value instead of an error.
 */

internal const val MINUTES_PER_DAY = 24 * 60

internal fun ListRow.toModel(): TaskList = TaskList(
    id = id,
    name = name,
    color = ListColor.fromToken(color),
    icon = icon,
    sortKey = sort_key,
    isInbox = is_inbox != 0L,
)

internal fun TaskRow.toModel(): TaskItem = TaskItem(
    id = id,
    listId = list_id,
    parentId = parent_id,
    title = title,
    notesMd = notes_md,
    priority = Priority.fromLevel(priority),
    dueDate = due_date?.let(::parseLocalDateOrNull),
    dueTime = due_time?.let(::minutesToLocalTimeOrNull),
    reminder = reminder_at?.let { at ->
        val offset = reminder_offset_min
        if (offset != null) {
            Reminder.BeforeDue(offset.toInt(), at, time_zone)
        } else {
            Reminder.Absolute(at, time_zone)
        }
    },
    rrule = rrule,
    repeatFrom = RepeatFrom.fromToken(repeat_from),
    seriesId = series_id,
    completedAt = completed_at,
    sortKey = sort_key,
)

/** `due_date` is `YYYY-MM-DD`. */
internal fun parseLocalDateOrNull(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

/** `due_time` is minutes after midnight. */
internal fun minutesToLocalTimeOrNull(minutes: Long): LocalTime? =
    if (minutes in 0 until MINUTES_PER_DAY) LocalTime(minutes.toInt() / 60, minutes.toInt() % 60) else null

internal fun LocalTime.toMinutes(): Long = hour * 60L + minute
