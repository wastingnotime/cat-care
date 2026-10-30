package org.wastingnotime.catcare

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.*
import kotlin.concurrent.thread

/** One durable alarm for the next event; timestamps, not app lifetime, drive delivery. */
object CareReminders {
    const val CHANNEL = "care-due"
    const val ACTION = "org.wastingnotime.catcare.REMIND"

    fun preferences(context: Context) = context.getSharedPreferences("reminders", 0)

    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL)
                ?.importance != NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED)

    data class Event(val task: CareStore.Task, val kind: String, val at: Instant) {
        val key
            get() = "${task.id}|${task.due}|$kind"
    }

    fun events(tasks: List<CareStore.Task>, minutes: Long): List<Event> =
        tasks
            .filter { it.state == "planned" && it.due.isNotEmpty() }
            .flatMap { task ->
                val due = Instant.parse(task.due)
                (if (minutes > 0) listOf(Event(task, "near", due.minusSeconds(minutes * 60)))
                else emptyList()) + Event(task, "due", due)
            }
            .sortedBy { it.at }

    @Synchronized
    fun sync(context: Context, now: Instant = Instant.now()) {
        val prefs = preferences(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CareReminderReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        alarm.cancel(pending)
        CareStore(context).use { store ->
            val tasks = store.tasks(null)
            val active =
                tasks
                    .filter { it.state == "planned" && it.due.isNotEmpty() }
                    .map { "${it.id}|${it.due}" }
                    .toSet()
            manager.activeNotifications
                .filter { it.tag !in active }
                .forEach { manager.cancel(it.tag, it.id) }
            val seen =
                prefs
                    .getStringSet("sent", emptySet())
                    .orEmpty()
                    .filter { key -> active.any { key.startsWith("$it|") } }
                    .toMutableSet()
            if (!prefs.getBoolean("enabled", true) || !allowed(context)) {
                manager.cancelAll()
                return
            }
            val events = events(tasks, prefs.getLong("minutes", 60))
            val cats = store.cats().associateBy { it.id }
            var next: Instant? = null
            events.forEach { event ->
                if (event.kind == "near" && !Instant.parse(event.task.due).isAfter(now))
                    return@forEach
                if (event.key in seen) return@forEach
                if (event.at.isAfter(now)) {
                    if (next == null || event.at.isBefore(next)) next = event.at
                } else {
                    val localized = notificationContext(context)
                    val title =
                        localized.getString(
                            if (event.kind == "near") R.string.reminder_near
                            else R.string.reminder_due,
                            cats[event.task.catId]?.name.orEmpty(),
                        )
                    val launch =
                        PendingIntent.getActivity(
                            context,
                            event.task.id.hashCode(),
                            Intent(context, MainActivity::class.java)
                                .setData(android.net.Uri.parse("catcare://task/${event.task.id}"))
                                .putExtra("taskId", event.task.id)
                                .setAction(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LAUNCHER)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                ),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    val locale =
                        if (localized.resources.configuration.locales[0].language == "pt")
                            java.util.Locale("pt", "BR")
                        else java.util.Locale.US
                    val text =
                        "${event.task.title}\n${CareDates.moment(Instant.parse(event.task.due), locale, ZoneId.systemDefault(), android.text.format.DateFormat.is24HourFormat(localized))}"
                    val notification =
                        NotificationCompat.Builder(localized, CHANNEL)
                            .setSmallIcon(R.drawable.ic_nav_cats)
                            .setContentTitle(title)
                            .setContentText(event.task.title)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                            .setContentIntent(launch)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(false)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .build()
                    try {
                        manager.notify("${event.task.id}|${event.task.due}", 0, notification)
                        seen.add(event.key)
                    } catch (_: SecurityException) {
                        /* Permission can be revoked during delivery. */
                    }
                }
            }
            prefs.edit(commit = true) { putStringSet("sent", seen) }
            next?.let {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pending)
            }
        }
    }

    private fun notificationContext(context: Context): Context {
        val choice = AppLanguage.choice(context)
        if (choice == "system") return context
        return context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply {
                setLocales(android.os.LocaleList.forLanguageTags(choice))
            }
        )
    }
}

class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    CareReminders.ACTION,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_LOCALE_CHANGED,
                )
        )
            return
        val pending = goAsync()
        thread(name = "care-reminders") {
            try {
                CareReminders.sync(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

@Composable
internal fun ReminderSettings() {
    val context = LocalContext.current
    val prefs = remember { CareReminders.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var minutes by remember { mutableStateOf(prefs.getLong("minutes", 60).toString()) }
    var allowed by remember { mutableStateOf(CareReminders.allowed(context)) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = CareReminders.allowed(context)
            thread { CareReminders.sync(context.applicationContext) }
        }
    Text(tr(R.string.reminders), style = MaterialTheme.typography.titleMedium)
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(tr(R.string.reminder_enable), androidx.compose.ui.Modifier.weight(1f))
        Switch(
            enabled,
            {
                enabled = it
                prefs.edit { putBoolean("enabled", it) }
                thread { CareReminders.sync(context.applicationContext) }
            },
            modifier =
                androidx.compose.ui.Modifier.semantics {
                    contentDescription = context.getString(R.string.reminder_enable)
                },
        )
    }
    if (enabled) {
        Choice(
            tr(R.string.reminder_before),
            linkedMapOf(
                "0" to tr(R.string.reminder_only_due),
                "15" to tr(R.string.reminder_15m),
                "60" to tr(R.string.reminder_1h),
                "1440" to tr(R.string.reminder_1d),
            ),
            minutes,
        ) {
            minutes = it
            prefs.edit { putLong("minutes", it.toLong()) }
            thread { CareReminders.sync(context.applicationContext) }
        }
        Text(tr(R.string.reminder_timing), color = Muted)
        if (!allowed)
            OutlinedButton(
                onClick = {
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    )
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                }
            ) {
                Text(tr(R.string.reminder_permission))
            }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        allowed = CareReminders.allowed(context)
        thread { CareReminders.sync(context.applicationContext) }
        onPauseOrDispose {}
    }
}
