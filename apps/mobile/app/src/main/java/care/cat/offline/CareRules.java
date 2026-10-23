package care.cat.offline;

import java.time.*;
import java.time.format.*;
import java.util.Locale;

/** Care semantics independent of Android and storage. Due times retain their named zone. */
public final class CareRules {
  private CareRules() {}

  public static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", new Locale("pt", "BR"))
          .withResolverStyle(ResolverStyle.STRICT);
  public static final DateTimeFormatter INPUT =
      DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", new Locale("pt", "BR"))
          .withResolverStyle(ResolverStyle.STRICT);

  public static String required(String value, String field) {
    if (value == null || value.trim().isEmpty())
      throw new IllegalArgumentException("Informe " + field + ".");
    return value.trim();
  }

  public static String date(String text) {
    if (text.trim().isEmpty()) return "";
    try {
      return LocalDate.parse(text.trim(), DATE).toString();
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Use uma data válida: dd/mm/aaaa.");
    }
  }

  public static void profile(String name, String birth, String adoption) {
    required(name, "o nome");
    if (!birth.isEmpty()) LocalDate.parse(birth);
    if (!adoption.isEmpty()) LocalDate.parse(adoption);
    if (!birth.isEmpty() && !adoption.isEmpty() && adoption.compareTo(birth) < 0)
      throw new IllegalArgumentException("A adoção não pode ser anterior ao nascimento.");
  }

  public static String due(String text, ZoneId zone) {
    if (text.trim().isEmpty()) return "";
    try {
      LocalDateTime local = LocalDateTime.parse(text.trim(), INPUT);
      if (zone.getRules().getValidOffsets(local).isEmpty())
        throw new IllegalArgumentException(
            "Horário inexistente por mudança de fuso. Escolha outro horário.");
      return local.atZone(zone).toString();
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Use data e hora válidas: dd/mm/aaaa hh:mm.");
    }
  }

  public static void recurrence(int days, int months) {
    if (days < 0 || months < 0 || (days > 0 && months > 0))
      throw new IllegalArgumentException("Escolha apenas uma recorrência positiva: dias ou meses.");
  }

  public static String successor(String due, int days, int months) {
    recurrence(days, months);
    if (days == 0 && months == 0) return "";
    if (due.isEmpty()) throw new IllegalArgumentException("A recorrência precisa de data e hora.");
    ZonedDateTime current = ZonedDateTime.parse(due);
    return (months > 0 ? current.plusMonths(months) : current.plusDays(days)).toString();
  }

  public static void defer(String oldDue, String nextDue, Instant now) {
    if (nextDue.isEmpty()) throw new IllegalArgumentException("Informe a nova data e hora.");
    Instant next = ZonedDateTime.parse(nextDue).toInstant();
    if (!next.isAfter(now)
        || (!oldDue.isEmpty() && !next.isAfter(ZonedDateTime.parse(oldDue).toInstant())))
      throw new IllegalArgumentException(
          "A nova data deve ser posterior à atual e ao prazo anterior.");
  }

  public static String status(String state, String due, Instant now) {
    if (state.equals("completed")) return "Concluído";
    if (state.equals("cancelled")) return "Cancelado";
    if (due.isEmpty()) return "Sem data definida";
    Instant instant = ZonedDateTime.parse(due).toInstant();
    if (instant.isBefore(now)) return "Prazo passado";
    if (!instant.isAfter(now.plus(Duration.ofDays(2)))) return "Em breve";
    return "Planejado";
  }

  public static String displayDue(String due) {
    if (due.isEmpty()) return "Sem data definida";
    ZonedDateTime date = ZonedDateTime.parse(due);
    return date.format(INPUT) + " · " + date.getZone().getId();
  }

  public static String displayDate(String value) {
    return value.isEmpty() ? "" : LocalDate.parse(value).format(DATE);
  }
}
