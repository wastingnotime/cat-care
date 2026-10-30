package org.wastingnotime.catcare

import java.time.*

enum class AgendaSection {
    OVERDUE,
    TODAY,
    UPCOMING,
    UNDATED,
}

/** Device-local calendar boundaries, chronological deadlines and deterministic ties. */
internal fun careAgenda(
    tasks: List<CareStore.Task>,
    now: Instant,
    zone: ZoneId,
): Map<AgendaSection, List<CareStore.Task>> {
    val today = now.atZone(zone).toLocalDate()
    return tasks
        .filter { it.state == "planned" }
        .sortedWith(
            compareBy<CareStore.Task> {
                    it.due.takeIf(String::isNotEmpty)?.let { due ->
                        ZonedDateTime.parse(due).toInstant()
                    } ?: Instant.MAX
                }
                .thenBy { it.title }
                .thenBy { it.id }
        )
        .groupBy {
            when {
                it.due.isEmpty() -> AgendaSection.UNDATED
                !ZonedDateTime.parse(it.due).toInstant().isAfter(now) -> AgendaSection.OVERDUE
                ZonedDateTime.parse(it.due).withZoneSameInstant(zone).toLocalDate() == today ->
                    AgendaSection.TODAY
                else -> AgendaSection.UPCOMING
            }
        }
}

internal fun historyDay(entry: CareStore.History, zone: ZoneId): LocalDate =
    historySourceDate(entry.details)?.let(LocalDate::parse)
        ?: Instant.parse(entry.at).atZone(zone).toLocalDate()
