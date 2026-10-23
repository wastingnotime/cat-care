package care.cat.offline

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.*

internal data class BackupStatus(val enabled: Boolean, val lastSuccess: Long, val failed: Boolean)

/** User-authorized document-tree access; the Android device-backup service stays disabled. */
internal class CareBackups(private val context: Context) {
    val preferences = context.getSharedPreferences("care_backups", Context.MODE_PRIVATE)
    fun status() = BackupStatus(preferences.contains("folder"), preferences.getLong("last_success", 0), preferences.getBoolean("failed", false))

    fun configure(uri: Uri) = synchronized(lock) {
        val old = preferences.getString("folder", null)
        context.contentResolver.takePersistableUriPermission(uri, FLAGS)
        try {
            val folder = folder(uri)
            val name = "cat-care-check-${UUID.randomUUID()}"
            val probe = requireNotNull(folder.createFile("application/octet-stream", "$name.tmp"))
            try { check(probe.renameTo("$name.json")) } finally { check(probe.delete()) }
            val editor = preferences.edit().putString("folder", uri.toString()).putBoolean("failed", false)
            if (old != uri.toString()) editor.remove("last_success")
            check(editor.commit())
        } catch (error: Exception) {
            if (old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(uri, FLAGS) }
            throw error
        }
        if (old != null && old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), FLAGS) }
        ensureScheduled()
    }

    fun ensureScheduled() {
        if (!status().enabled) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        check(scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, CareBackupJob::class.java))
            .setPeriodic(24 * 60 * 60 * 1000L).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS)
    }

    fun disable() = synchronized(lock) {
        val old = preferences.getString("folder", null)
        check(preferences.edit().clear().commit())
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        if (old != null) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), FLAGS) }
    }

    fun write() = synchronized(lock) {
        val selected = preferences.getString("folder", null) ?: return@synchronized
        try {
            val folder = folder(Uri.parse(selected))
            folder.listFiles().filter {
                it.isFile && it.name?.matches(PENDING) == true &&
                    it.lastModified() > 0 && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            }.forEach { check(it.delete()) }
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now())
            val name = "cat-care-auto-$timestamp-${UUID.randomUUID()}.json"
            val pending = requireNotNull(folder.createFile("application/octet-stream", "cat-care-pending-${UUID.randomUUID()}.tmp"))
            try {
                val json = CareStore(context).use { it.export(null) }
                require(json.toByteArray(Charsets.UTF_8).size <= CareBackupDocument.MAX_BYTES)
                requireNotNull(context.contentResolver.openOutputStream(pending.uri, "wt"))
                    .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                check(pending.renameTo(name))
            } catch (error: Exception) {
                runCatching { pending.delete() }
                throw error
            }
            // Only this app's completed backup names participate in retention.
            folder.listFiles().filter { it.isFile && it.name?.matches(COMPLETED) == true }
                .sortedByDescending { it.name }.drop(7).forEach { check(it.delete()) }
            check(preferences.edit().putLong("last_success", System.currentTimeMillis()).putBoolean("failed", false).commit())
        } catch (error: Exception) {
            preferences.edit().putBoolean("failed", true).commit()
            throw error
        }
    }

    private fun folder(uri: Uri) = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        .also { check(it.isDirectory && it.canWrite()) }

    companion object {
        private val lock = Any()
        private const val JOB_ID = 23001
        private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private val PENDING = Regex("^cat-care-pending-[0-9a-f-]{36}\\.tmp$")
        private val COMPLETED = Regex("^cat-care-auto-\\d{8}-\\d{6}-\\d{3}-[0-9a-f-]{36}\\.json$")
    }
}

class CareBackupJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.Main).launch {
            val retry = try {
                withContext(Dispatchers.IO) { CareBackups(applicationContext).write() }
                false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { work?.cancel(); super.onDestroy() }
}
