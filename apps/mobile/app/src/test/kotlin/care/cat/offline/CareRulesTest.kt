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
        assertEquals("unknown", CareRules.status("planned", "", now))
        assertEquals("soon", CareRules.status("planned", "2026-10-24T12:00Z", now))
        assertEquals("overdue", CareRules.status("planned", "2026-10-22T11:59Z", now))
        assertEquals("completed", CareRules.status("completed", "", now))
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

    @Test
    fun schedulePartsValidateTimeAndDstWithoutLocaleParsing() {
        val zone = ZoneId.of("America/New_York")
        assertEquals("", CareRules.due("", "", zone))
        assertEquals(
            CareIssue.TIME,
            assertThrows(CareValidation::class.java) { CareRules.due("2026-10-23", "", zone) }.issue,
        )
        assertEquals(
            CareIssue.DST,
            assertThrows(CareValidation::class.java) { CareRules.due("2026-03-08", "02:30", zone) }
                .issue,
        )
        assertEquals(
            "2026-11-01T01:30-04:00[America/New_York]",
            CareRules.due("2026-11-01", "01:30", zone),
        )
        assertEquals(
            "2026-10-23T09:30-03:00[America/Sao_Paulo]",
            CareRules.due("2026-10-23", "09:30", ZoneId.of("America/Sao_Paulo")),
        )
    }

    @Test
    fun localizedDatesUseDeviceZoneAndExplicitOffset() {
        val instant = Instant.parse("2026-10-24T01:30:00Z")
        val zone = ZoneId.of("America/Sao_Paulo")
        assertEquals(
            "23/10/2026 22:30 · America/Sao_Paulo (UTC-03:00)",
            CareDates.moment(instant, java.util.Locale("pt", "BR"), zone, true),
        )
        assertEquals(
            "10/23/2026 10:30 PM · America/Sao_Paulo (UTC-03:00)",
            CareDates.moment(instant, java.util.Locale.US, zone, false),
        )
        assertEquals("01/02/2026", CareDates.date("2026-02-01", java.util.Locale("pt", "BR")))
        assertEquals("02/01/2026", CareDates.date("2026-02-01", java.util.Locale.US))
        assertTrue(
            CareDates.moment(
                    Instant.parse("2026-11-01T05:30:00Z"),
                    java.util.Locale.US,
                    ZoneId.of("America/New_York"),
                    false,
                )
                .endsWith("(UTC-04:00)")
        )
        assertTrue(
            CareDates.moment(
                    Instant.parse("2026-11-01T06:30:00Z"),
                    java.util.Locale.US,
                    ZoneId.of("America/New_York"),
                    false,
                )
                .endsWith("(UTC-05:00)")
        )
    }

    private fun reject(action: () -> Unit) =
        assertThrows(IllegalArgumentException::class.java) { action() }
}
