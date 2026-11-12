package org.wastingnotime.catcare

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val backups = CareBackups(context)
    private val uri = Uri.parse("content://org.wastingnotime.catcare.test.backups/tree/root")
    private lateinit var folder: DocumentFile

    @Before fun setup() {
        backups.disable()
        context.startActivity(Intent().setClassName("org.wastingnotime.catcare.test", BackupGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = System.currentTimeMillis() + 5000
        while (context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            check(System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        }
        folder = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        folder.listFiles().forEach { it.delete() }
        context.deleteDatabase("cat-care.db")
        CareStore(context).use { it.saveCat(null, "Backup cat", "", "", "gray") }
        backups.configure(uri)
    }

    @After fun cleanup() {
        backups.disable()
        if (::folder.isInitialized) folder.listFiles().forEach { it.delete() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        context.deleteDatabase("cat-care.db")
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        CareReminders.preferences(context).edit().clear().commit()
    }

    @Test fun restoresPortableSettingsAndRequiresFolderAccessAfterReinstall() {
        context.getSharedPreferences("appearance", 0).edit().putString("theme", "dark").commit()
        CareReminders.preferences(context).edit().putBoolean("enabled", false).putLong("minutes", 15).commit()
        backups.write()
        val file = folder.listFiles().filter { it.name?.startsWith("cat-care-auto-") == true }.maxBy { it.name.orEmpty() }
        val text = requireNotNull(context.contentResolver.openInputStream(file.uri)).bufferedReader().use { it.readText() }
        backups.disable() // Releases the persisted grant, as uninstall would.
        context.getSharedPreferences("appearance", 0).edit().putString("theme", "light").commit()
        CareReminders.preferences(context).edit().putBoolean("enabled", true).commit()
        CareStore(context).use { it.restore(text) }
        assertEquals("dark", context.getSharedPreferences("appearance", 0).getString("theme", ""))
        assertFalse(CareReminders.preferences(context).getBoolean("enabled", true))
        assertEquals(15L, CareReminders.preferences(context).getLong("minutes", 0))
        assertEquals(uri.toString(), backups.status().folder)
        assertTrue(backups.status().needsAccess)
        assertFalse(backups.status().enabled)
        assertTrue(backups.status().lastSuccess > 0)
        assertNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(23001))
        backups.configure(uri)
        assertTrue(backups.status().enabled)
        assertTrue(backups.status().lastSuccess > 0)
    }

    @Test fun legacyBackupPreservesSettingsAndInvalidSettingsRejectBeforeReplacement() {
        CareStore(context).use { store ->
            val root = org.json.JSONObject(store.export(null))
            root.getJSONObject("settings").put("reminder_minutes", -1)
            assertThrows(Exception::class.java) { store.restore(root.toString()) }
            assertEquals(1, store.cats().size)
            root.put("version", 3).remove("settings")
            context.getSharedPreferences("appearance", 0).edit().putString("theme", "light").commit()
            store.restore(root.toString())
            assertEquals("light", context.getSharedPreferences("appearance", 0).getString("theme", ""))
            assertTrue(backups.status().enabled)
        }
    }

    @Test fun automaticCopiesAreRestorableAndKeepSevenWithoutDeletingUnrelatedFiles() {
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission })
        val unrelated = requireNotNull(folder.createFile("application/json", "owner-notes.json"))
        repeat(8) { backups.write() }
        val completed = folder.listFiles().filter { it.name?.startsWith("cat-care-auto-") == true }
        assertEquals(7, completed.size)
        assertTrue(unrelated.exists())
        completed.forEach { file ->
            val text = requireNotNull(context.contentResolver.openInputStream(file.uri)).bufferedReader().use { it.readText() }
            assertEquals(1, CareBackupDocument.parse(text).count("cats"))
        }
        assertTrue(backups.status().lastSuccess > 0)
        assertFalse(backups.status().failed)
        backups.ensureScheduled()
        val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
        assertTrue(scheduler.allPendingJobs.any { it.service.className == CareBackupJob::class.java.name && it.isPersisted && it.isPeriodic })
        val previousSuccess = backups.status().lastSuccess
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd jobscheduler run -f org.wastingnotime.catcare 23001").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        val deadline = System.currentTimeMillis() + 10000
        while (backups.status().lastSuccess <= previousSuccess) {
            check(System.currentTimeMillis() < deadline) { "Scheduled backup did not finish" }
            Thread.sleep(50)
        }
        backups.disable()
        assertEquals(7, folder.listFiles().count { it.name?.startsWith("cat-care-auto-") == true })
        assertFalse(scheduler.allPendingJobs.any { it.service.className == CareBackupJob::class.java.name })
    }

    @Test fun failedPublishPreservesLastSuccessfulCopyAndReportsFailure() {
        backups.write()
        val success = backups.status().lastSuccess
        requireNotNull(folder.createFile("text/plain", "reject-rename"))
        assertThrows(Exception::class.java) { backups.write() }
        assertEquals(success, backups.status().lastSuccess)
        assertTrue(backups.status().failed)
        assertEquals(1, folder.listFiles().count { it.name?.startsWith("cat-care-auto-") == true })
        assertEquals(0, folder.listFiles().count { it.name?.startsWith("cat-care-pending-") == true })
    }
}
