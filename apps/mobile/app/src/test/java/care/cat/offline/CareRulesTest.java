package care.cat.offline;

import static org.junit.Assert.*;

import java.time.*;
import org.junit.Test;

public class CareRulesTest {
  @Test
  public void monthlyRecurrenceClampsAndPreservesWallTime() {
    String next = CareRules.successor("2027-01-31T09:00-03:00[America/Sao_Paulo]", 0, 1);
    ZonedDateTime date = ZonedDateTime.parse(next);
    assertEquals(LocalDate.of(2027, 2, 28), date.toLocalDate());
    assertEquals(LocalTime.of(9, 0), date.toLocalTime());
    assertEquals(ZoneId.of("America/Sao_Paulo"), date.getZone());
    assertEquals(
        LocalDate.of(2027, 3, 28),
        ZonedDateTime.parse(CareRules.successor(next, 0, 1)).toLocalDate());
  }

  @Test
  public void dailyRecurrencePreservesLocalTimeAcrossDst() {
    ZonedDateTime next =
        ZonedDateTime.parse(CareRules.successor("2026-03-07T09:00-05:00[America/New_York]", 1, 0));
    assertEquals(LocalTime.of(9, 0), next.toLocalTime());
    assertEquals(ZoneOffset.ofHours(-4), next.getOffset());
  }

  @Test
  public void statusDistinguishesUncertaintyFromScheduling() {
    Instant now = Instant.parse("2026-10-22T12:00:00Z");
    assertEquals("Sem data definida", CareRules.status("planned", "", now));
    assertEquals("Em breve", CareRules.status("planned", "2026-10-24T12:00Z", now));
    assertEquals("Prazo passado", CareRules.status("planned", "2026-10-22T11:59Z", now));
    assertEquals("Concluído", CareRules.status("completed", "", now));
  }

  @Test
  public void invalidDatesAndRecurrenceAreRejected() {
    reject(() -> CareRules.date("31/02/2026"));
    reject(() -> CareRules.profile("Mimi", "2026-10-22", "2026-10-21"));
    reject(() -> CareRules.profile(" ", "", ""));
    reject(() -> CareRules.recurrence(-1, 0));
    reject(() -> CareRules.recurrence(1, 1));
    reject(() -> CareRules.successor("", 1, 0));
    reject(() -> CareRules.due("08/03/2026 02:30", ZoneId.of("America/New_York")));
  }

  @Test
  public void deferRequiresFutureAndLaterThanPrevious() {
    Instant now = Instant.parse("2026-10-22T12:00:00Z");
    reject(() -> CareRules.defer("2026-10-24T12:00Z", "2026-10-24T12:00Z", now));
    reject(() -> CareRules.defer("", "2026-10-22T12:00Z", now));
    CareRules.defer("2026-10-24T12:00Z", "2026-10-25T12:00Z", now);
  }

  private static void reject(Runnable action) {
    try {
      action.run();
      fail("Expected invalid input");
    } catch (IllegalArgumentException expected) {
    }
  }
}
