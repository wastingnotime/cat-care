package care.cat.offline

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CareRulesTest {
    @Test
    fun monthlyRecurrenceClampsAndPreservesWallTime() {
        val next = CareRules.successor("2027-01-31T09:00-03:00[America/Sao_Paulo]", 0, 1)
        val date = ZonedDateTime.parse(next)
        assertEquals(LocalDate.of(2027, 2, 28), date.toLocalDate())
        assertEquals(LocalTime.of(9, 0), date.toLocalTime())
        assertEquals(ZoneId.of("America/Sao_Paulo"), date.zone)
        assertEquals(
            LocalDate.of(2027, 3, 28),
            ZonedDateTime.parse(CareRules.successor(next, 0, 1)).toLocalDate(),
        )
    }

    @Test
    fun dailyRecurrencePreservesLocalTimeAcrossDst() {
        val next =
            ZonedDateTime.parse(
                CareRules.successor("2026-03-07T09:00-05:00[America/New_York]", 1, 0)
            )
        assertEquals(LocalTime.of(9, 0), next.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), next.offset)
    }

    @Test
    fun statusDistinguishesUncertaintyFromScheduling() {
        val now = Instant.parse("2026-10-22T12:00:00Z")
        assertEquals("Sem data definida", CareRules.status("planned", "", now))
        assertEquals("Em breve", CareRules.status("planned", "2026-10-24T12:00Z", now))
        assertEquals("Prazo passado", CareRules.status("planned", "2026-10-22T11:59Z", now))
        assertEquals("Concluído", CareRules.status("completed", "", now))
    }

    @Test
    fun invalidDatesAndRecurrenceAreRejected() {
        reject { CareRules.date("31/02/2026") }
        reject { CareRules.profile("Mimi", "2026-10-22", "2026-10-21") }
        reject { CareRules.profile(" ", "", "") }
        reject { CareRules.recurrence(-1, 0) }
        reject { CareRules.recurrence(1, 1) }
        reject { CareRules.successor("", 1, 0) }
        reject { CareRules.due("08/03/2026 02:30", ZoneId.of("America/New_York")) }
    }

    @Test
    fun deferRequiresFutureAndLaterThanPrevious() {
        val now = Instant.parse("2026-10-22T12:00:00Z")
        reject { CareRules.defer("2026-10-24T12:00Z", "2026-10-24T12:00Z", now) }
        reject { CareRules.defer("", "2026-10-22T12:00Z", now) }
        CareRules.defer("2026-10-24T12:00Z", "2026-10-25T12:00Z", now)
    }

    private fun reject(action: () -> Unit) =
        assertThrows(IllegalArgumentException::class.java) { action() }
}
