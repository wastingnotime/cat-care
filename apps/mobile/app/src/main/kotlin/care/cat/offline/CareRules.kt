package care.cat.offline

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Care semantics independent of Android and storage. Due times retain their named zone. */
object CareRules {
    val DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale("pt", "BR"))
            .withResolverStyle(ResolverStyle.STRICT)
    val INPUT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale("pt", "BR"))
            .withResolverStyle(ResolverStyle.STRICT)

    fun required(value: String?, field: String): String {
        require(!value.isNullOrBlank()) { "Informe $field." }
        return value.trim()
    }

    fun date(text: String): String {
        if (text.isBlank()) return ""
        return try {
            LocalDate.parse(text.trim(), DATE).toString()
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Use uma data válida: dd/mm/aaaa.")
        }
    }

    fun profile(name: String, birth: String, adoption: String) {
        required(name, "o nome")
        if (birth.isNotEmpty()) LocalDate.parse(birth)
        if (adoption.isNotEmpty()) LocalDate.parse(adoption)
        require(birth.isEmpty() || adoption.isEmpty() || adoption >= birth) {
            "A adoção não pode ser anterior ao nascimento."
        }
    }

    fun due(text: String, zone: ZoneId): String {
        if (text.isBlank()) return ""
        return try {
            val local = LocalDateTime.parse(text.trim(), INPUT)
            require(zone.rules.getValidOffsets(local).isNotEmpty()) {
                "Horário inexistente por mudança de fuso. Escolha outro horário."
            }
            local.atZone(zone).toString()
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Use data e hora válidas: dd/mm/aaaa hh:mm.")
        }
    }

    fun recurrence(days: Int, months: Int) {
        require(days >= 0 && months >= 0 && (days == 0 || months == 0)) {
            "Escolha apenas uma recorrência positiva: dias ou meses."
        }
    }

    fun successor(due: String, days: Int, months: Int): String {
        recurrence(days, months)
        if (days == 0 && months == 0) return ""
        require(due.isNotEmpty()) { "A recorrência precisa de data e hora." }
        val current = ZonedDateTime.parse(due)
        return (if (months > 0) current.plusMonths(months.toLong())
            else current.plusDays(days.toLong()))
            .toString()
    }

    fun defer(oldDue: String, nextDue: String, now: Instant) {
        require(nextDue.isNotEmpty()) { "Informe a nova data e hora." }
        val next = ZonedDateTime.parse(nextDue).toInstant()
        require(
            next.isAfter(now) &&
                (oldDue.isEmpty() || next.isAfter(ZonedDateTime.parse(oldDue).toInstant()))
        ) {
            "A nova data deve ser posterior à atual e ao prazo anterior."
        }
    }

    fun status(state: String, due: String, now: Instant): String =
        when {
            state == "completed" -> "Concluído"
            state == "cancelled" -> "Cancelado"
            due.isEmpty() -> "Sem data definida"
            ZonedDateTime.parse(due).toInstant().isBefore(now) -> "Prazo passado"
            !ZonedDateTime.parse(due).toInstant().isAfter(now.plus(Duration.ofDays(2))) ->
                "Em breve"
            else -> "Planejado"
        }

    fun displayDue(due: String): String =
        if (due.isEmpty()) "Sem data definida"
        else {
            val date = ZonedDateTime.parse(due)
            "${date.format(INPUT)} · ${date.zone.id}"
        }

    fun displayDate(value: String): String =
        if (value.isEmpty()) "" else LocalDate.parse(value).format(DATE)
}
