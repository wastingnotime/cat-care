package care.cat.offline

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

enum class CareIssue {
    NAME,
    TITLE,
    CATEGORY,
    DESCRIPTION,
    COMMENT,
    CAT,
    DATE,
    TIME,
    DATETIME,
    ADOPTION,
    DST,
    RECURRENCE,
    RECURRENCE_DATE,
    DEFER,
    CAT_MISSING,
    CAT_INCORRECT,
    TASK_MISSING,
    STALE,
    ACTION,
    TYPE,
    OBSERVATION,
    COAT,
    VERSION,
}

class CareValidation(val issue: CareIssue) : IllegalArgumentException(issue.name)

fun careRequire(condition: Boolean, issue: CareIssue) {
    if (!condition) throw CareValidation(issue)
}

/**
 * Language-independent scheduling rules. Timestamps persist as UTC; recurrence uses a separate
 * named zone.
 */
object CareRules {
    // Legacy textual timestamps are retained only for exported historical descriptions.
    val DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale("pt", "BR"))
            .withResolverStyle(ResolverStyle.STRICT)
    val INPUT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale("pt", "BR"))
            .withResolverStyle(ResolverStyle.STRICT)

    fun required(value: String?, field: CareIssue): String {
        careRequire(!value.isNullOrBlank(), field)
        return value!!.trim()
    }

    fun date(text: String): String =
        try {
            if (text.isBlank()) "" else LocalDate.parse(text.trim(), DATE).toString()
        } catch (_: DateTimeException) {
            throw CareValidation(CareIssue.DATE)
        }

    fun profile(name: String, birth: String, adoption: String) {
        required(name, CareIssue.NAME)
        if (birth.isNotEmpty()) LocalDate.parse(birth)
        if (adoption.isNotEmpty()) LocalDate.parse(adoption)
        careRequire(birth.isEmpty() || adoption.isEmpty() || adoption >= birth, CareIssue.ADOPTION)
    }

    fun due(text: String, zone: ZoneId): String {
        if (text.isBlank()) return ""
        return try {
            zoned(LocalDateTime.parse(text.trim(), INPUT), zone)
        } catch (_: DateTimeException) {
            throw CareValidation(CareIssue.DATETIME)
        }
    }

    fun due(date: String, time: String, zone: ZoneId): String {
        if (date.isEmpty() && time.isEmpty()) return ""
        careRequire(date.isNotEmpty(), CareIssue.DATE)
        careRequire(time.isNotEmpty(), CareIssue.TIME)
        return try {
            zoned(LocalDate.parse(date).atTime(LocalTime.parse(time)), zone)
        } catch (_: DateTimeException) {
            throw CareValidation(CareIssue.DATETIME)
        }
    }

    private fun zoned(local: LocalDateTime, zone: ZoneId): String {
        careRequire(zone.rules.getValidOffsets(local).isNotEmpty(), CareIssue.DST)
        return local.atZone(zone).toInstant().toString()
    }

    fun recurrence(days: Int, months: Int) {
        careRequire(days >= 0 && months >= 0 && (days == 0 || months == 0), CareIssue.RECURRENCE)
    }

    fun successor(
        due: String,
        days: Int,
        months: Int,
        zone: ZoneId = ZonedDateTime.parse(due.ifEmpty { "1970-01-01T00:00Z" }).zone,
    ): String {
        recurrence(days, months)
        if (days == 0 && months == 0) return ""
        careRequire(due.isNotEmpty(), CareIssue.RECURRENCE_DATE)
        val current = ZonedDateTime.parse(due).withZoneSameInstant(zone)
        return (if (months > 0) current.plusMonths(months.toLong())
            else current.plusDays(days.toLong()))
            .toInstant()
            .toString()
    }

    fun defer(oldDue: String, nextDue: String, now: Instant) {
        careRequire(nextDue.isNotEmpty(), CareIssue.DATETIME)
        val next = ZonedDateTime.parse(nextDue).toInstant()
        careRequire(
            next.isAfter(now) &&
                (oldDue.isEmpty() || next.isAfter(ZonedDateTime.parse(oldDue).toInstant())),
            CareIssue.DEFER,
        )
    }

    fun status(state: String, due: String, now: Instant): String =
        when {
            state == "completed" -> "completed"
            state == "cancelled" -> "cancelled"
            due.isEmpty() -> "unknown"
            ZonedDateTime.parse(due).toInstant().let { !it.isAfter(now) } -> "overdue"
            !ZonedDateTime.parse(due).toInstant().isAfter(now.plus(Duration.ofDays(2))) -> "soon"
            else -> "planned"
        }

    fun displayDue(due: String): String =
        if (due.isEmpty()) "Sem data definida"
        else {
            val date = ZonedDateTime.parse(due)
            "${date.format(INPUT)} · ${date.zone.id}"
        }
}
