package org.wastingnotime.catcare

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreOnboardingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val backups = CareBackups(context)
    private val folder = Uri.parse("content://org.wastingnotime.catcare.test.backups/tree/root")
    private val file = File(context.cacheDir, "onboarding-backup.json")
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() = reset()
        override fun after() = reset()

        private fun reset() {
            backups.disable()
            context.deleteDatabase("cat-care.db")
            file.delete()
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun cancelledAndInvalidFilesLeaveWelcomeAvailableForRetry() {
        pickBackup(null)
        text(R.string.add_first_cat).assertExists()
        file.writeText("{}")
        pickBackup(Uri.fromFile(file))
        waitFor(hasText(compose.activity.getString(R.string.restore_error)))
        text(R.string.add_first_cat).assertExists()
        CareStore(context).use { assertTrue(it.cats().isEmpty()) }

        writeBackup()
        previewBackup()
        compose.onNodeWithTag("close-editor").performClick()
        text(R.string.add_first_cat).assertExists()
        CareStore(context).use { assertTrue(it.cats().isEmpty()) }

        previewBackup()
        confirmRestore()
        text(R.string.restore_backup_choose_folder).assertIsDisplayed()
        compose.onNodeWithTag("choose-backup-folder").performScrollTo().assertIsEnabled()
        compose.activityRule.scenario.recreate()
        text(R.string.restore_backup_choose_folder).assertIsDisplayed()
        compose.onNodeWithTag("navigate-up").performClick()
        text(R.string.add_first_cat).assertDoesNotExist()
        compose.onNodeWithTag("nav-cats").performClick()
        compose.onNodeWithText("Restored cat").assertExists()
    }

    @Test fun rememberedFolderNeedsAccessAgain() {
        writeBackup(rememberFolder = true)
        previewBackup()
        confirmRestore()
        text(R.string.restore_backup_reauthorize).assertIsDisplayed()
        assertTrue(backups.status().needsAccess)
        assertEquals(folder.toString(), backups.status().folder)
    }

    @Test fun restoreKeepsOwnerOnTodayWhenBackupsAreAlreadyActive() {
        grantFolderAccess()
        backups.configure(folder)
        writeBackup(rememberFolder = true)
        previewBackup()
        confirmRestore()
        compose.onNodeWithTag("nav-today").assertIsDisplayed()
        compose.onNodeWithTag("restore-backup-guidance").assertDoesNotExist()
        assertTrue(backups.status().enabled)
    }

    private fun writeBackup(rememberFolder: Boolean = false) {
        val name = "onboarding-source.db"
        try {
            val snapshot = CareStore(context, name).use { source ->
                source.saveCat(null, "Restored cat", "", "", "gray")
                JSONObject(source.export(null))
            }
            if (rememberFolder) snapshot.getJSONObject("settings").put("backup_folder", folder.toString())
            file.writeText(snapshot.toString())
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun previewBackup() {
        pickBackup(Uri.fromFile(file))
        waitFor(hasTestTag("confirm-restore"))
        val counts = compose.activity.getString(R.string.restore_preview, 1, 0, 1, 0)
        compose.onNodeWithText(counts).assertExists()
    }

    private fun confirmRestore() {
        compose.onNodeWithTag("confirm-restore").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("confirm-restore").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty()
        }
        CareStore(context).use { assertEquals("Restored cat", it.cats().single().name) }
    }

    private fun pickBackup(uri: Uri?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var launched = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                launched = true
                return if (uri == null) Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                else Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val label = compose.activity.getString(R.string.restore_action)
            waitFor(hasText(label) and isEnabled())
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertTrue(launched) }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun grantFolderAccess() {
        context.startActivity(
            Intent().setClassName("org.wastingnotime.catcare.test", BackupGrantActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        compose.waitUntil(10000) {
            context.checkUriPermission(
                folder, android.os.Process.myPid(), android.os.Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun text(resource: Int) = compose.onNodeWithText(compose.activity.getString(resource))

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(10000) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}
