package care.cat.offline

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

object AppLanguage {
    fun choice(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context
                .getSystemService(LocaleManager::class.java)
                .applicationLocales
                .toLanguageTags()
                .ifEmpty { "system" }
        } else
            context.getSharedPreferences("appearance", 0).getString("language", "system")
                ?: "system"

    fun context(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = choice(base)
        if (tag == "system") {
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        Locale.setDefault(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
        )
    }

    fun change(activity: Activity, tag: String) {
        require(tag in listOf("system", "pt-BR", "en-US"))
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(if (tag == "system") "" else tag)
        } else {
            activity.getSharedPreferences("appearance", 0).edit { putString("language", tag) }
            activity.recreate()
        }
    }
}

@Composable fun tr(id: Int, vararg args: Any): String = stringResource(id, *args)

@Composable fun quantity(id: Int, count: Int): String = pluralStringResource(id, count, count)

@Composable
fun careLocale(): Locale =
    if (LocalConfiguration.current.locales[0].language == "pt") Locale("pt", "BR") else Locale.US

@Composable fun localDate(value: String): String = CareDates.date(value, careLocale())

@Composable
fun localMoment(value: String): String =
    if (value.isEmpty()) tr(R.string.status_unknown)
    else
        CareDates.moment(
            ZonedDateTime.parse(value).toInstant(),
            careLocale(),
            ZoneId.systemDefault(),
            DateFormat.is24HourFormat(LocalContext.current),
        )

@Composable
fun historyTime(value: String): String =
    CareDates.moment(
        Instant.parse(value),
        careLocale(),
        ZoneId.systemDefault(),
        DateFormat.is24HourFormat(LocalContext.current),
    )

/** Imported calendar dates have no time or timezone conversion; `at` is only a sort anchor. */
@Composable
internal fun historyDateLabel(entry: CareStore.History): String {
    val date = historySourceDate(entry.details)
    return if (date != null) tr(R.string.historical_date, localDate(date))
    else tr(R.string.recorded_at, historyTime(entry.at))
}

internal fun historySourceDate(details: String?): String? =
    runCatching {
            val metadata = details?.let(::JSONObject) ?: return null
            if (metadata.optString("date_precision") != "day") return null
            LocalDate.parse(metadata.getString("occurred_on")).toString()
        }
        .getOrNull()

/** Locale controls presentation; all persisted dates/times remain ISO with explicit zones. */
object CareDates {
    fun date(value: String, locale: Locale): String =
        if (value.isEmpty()) ""
        else
            LocalDate.parse(value)
                .format(
                    DateTimeFormatter.ofPattern(
                        if (locale.language == "pt") "dd/MM/uuuu" else "MM/dd/uuuu",
                        locale,
                    )
                )

    fun time(value: String, locale: Locale, use24Hour: Boolean): String =
        if (value.isEmpty()) ""
        else
            LocalTime.parse(value)
                .format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))

    fun moment(value: Instant, locale: Locale, zone: ZoneId, use24Hour: Boolean): String {
        val local = value.atZone(zone)
        val pattern =
            (if (locale.language == "pt") "dd/MM/uuuu" else "MM/dd/uuuu") +
                if (use24Hour) " HH:mm" else " h:mm a"
        val offset = local.offset.id.takeUnless { it == "Z" } ?: "+00:00"
        return "${local.format(DateTimeFormatter.ofPattern(pattern, locale))} · ${zone.id} (UTC$offset)"
    }
}

fun categoryLabel(category: String): Int =
    when (category) {
        "Alimentação",
        "feeding" -> R.string.category_feeding
        "Higiene",
        "hygiene" -> R.string.category_hygiene
        "Medicação",
        "medication" -> R.string.category_medication
        "Veterinário",
        "vet" -> R.string.category_vet
        else -> R.string.category_other
    }

val Categories = listOf("Alimentação", "Higiene", "Medicação", "Veterinário", "Outro")
val RecordTypes = listOf("measurement", "feeding", "medication", "hygiene", "care", "observation")

fun typeLabel(type: String): Int =
    when (type) {
        "measurement" -> R.string.type_measurement
        "feeding",
        "medication",
        "hygiene" -> categoryLabel(type)
        "care" -> R.string.type_care
        "observation" -> R.string.type_observation
        "created" -> R.string.type_created
        "edited" -> R.string.type_edited
        "completed" -> R.string.type_completed
        "cancelled" -> R.string.type_cancelled
        "deferred" -> R.string.type_deferred
        else -> R.string.type_profile
    }

fun statusLabel(status: String): Int =
    when (status) {
        "completed" -> R.string.status_completed
        "cancelled" -> R.string.status_cancelled
        "unknown" -> R.string.status_unknown
        "overdue" -> R.string.status_overdue
        "soon" -> R.string.status_soon
        else -> R.string.status_planned
    }

fun issueLabel(issue: CareIssue): Int =
    when (issue) {
        CareIssue.NAME -> R.string.error_name
        CareIssue.TITLE -> R.string.error_title
        CareIssue.CATEGORY -> R.string.error_category
        CareIssue.DESCRIPTION -> R.string.error_description
        CareIssue.COMMENT -> R.string.error_comment
        CareIssue.CAT -> R.string.error_cat
        CareIssue.DATE -> R.string.error_date
        CareIssue.TIME -> R.string.time_required
        CareIssue.DATETIME -> R.string.error_datetime
        CareIssue.ADOPTION -> R.string.error_adoption
        CareIssue.DST -> R.string.error_dst
        CareIssue.RECURRENCE -> R.string.error_recurrence
        CareIssue.RECURRENCE_DATE -> R.string.error_recurrence_date
        CareIssue.DEFER -> R.string.error_defer
        CareIssue.CAT_MISSING -> R.string.error_cat_missing
        CareIssue.CAT_INCORRECT -> R.string.error_cat_incorrect
        CareIssue.TASK_MISSING -> R.string.error_task_missing
        CareIssue.STALE -> R.string.error_stale
        CareIssue.ACTION -> R.string.error_action
        CareIssue.TYPE -> R.string.error_type
        CareIssue.OBSERVATION -> R.string.error_observation
        CareIssue.COAT -> R.string.error_coat
        CareIssue.VERSION -> R.string.error_version
    }
