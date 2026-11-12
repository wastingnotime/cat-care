package org.wastingnotime.catcare

import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import org.json.JSONObject

/** Portable preferences only: never notification permission or delivered-alarm bookkeeping. */
internal object CareBackupSettings {
    fun snapshot(context: Context): JSONObject {
        val appearance = context.getSharedPreferences("appearance", 0)
        val reminders = CareReminders.preferences(context)
        val backups = CareBackups(context).preferences
        return JSONObject()
            .put("theme", appearance.getString("theme", "system"))
            .put("language", AppLanguage.choice(context))
            .put("reminders_enabled", reminders.getBoolean("enabled", true))
            .put("reminder_minutes", reminders.getLong("minutes", 60))
            .put("backup_folder", backups.getString("folder", null) ?: JSONObject.NULL)
            .put("backup_last_success", backups.getLong("last_success", 0))
            .put("backup_failed", backups.getBoolean("failed", false))
    }

    fun validate(value: JSONObject): JSONObject {
        require(value.get("theme") in listOf("system", "light", "dark"))
        require(value.get("language") in listOf("system", "pt-BR", "en-US"))
        require(value.get("reminders_enabled") is Boolean && value.get("backup_failed") is Boolean)
        for (key in listOf("reminder_minutes", "backup_last_success")) {
            val number = value.get(key)
            require((number is Int || number is Long) && (number as Number).toLong() >= 0)
        }
        require(value.getLong("reminder_minutes") <= 10080)
        if (!value.isNull("backup_folder")) {
            require(value.get("backup_folder") is String)
            val uri = Uri.parse(value.getString("backup_folder"))
            require(uri.scheme == "content" && android.provider.DocumentsContract.isTreeUri(uri))
        }
        return value
    }

    fun restore(context: Context, value: JSONObject) {
        check(context.getSharedPreferences("appearance", 0).edit()
            .putString("theme", value.getString("theme"))
            .putString("language", value.getString("language")).commit())
        check(CareReminders.preferences(context).edit().clear()
            .putBoolean("enabled", value.getBoolean("reminders_enabled"))
            .putLong("minutes", value.getLong("reminder_minutes")).commit())
        val backups = CareBackups(context)
        // A saved URI is a hint, not a permission grant. Scheduling checks live access.
        check(backups.preferences.edit().clear()
            .putString("folder", if (value.isNull("backup_folder")) null else value.getString("backup_folder"))
            .putLong("last_success", value.getLong("backup_last_success"))
            .putBoolean("failed", value.getBoolean("backup_failed")).commit())
        context.getSystemService(android.app.job.JobScheduler::class.java).cancel(23001)
        backups.ensureScheduled()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(value.getString("language").takeUnless { it == "system" } ?: "")
        }
    }
}
