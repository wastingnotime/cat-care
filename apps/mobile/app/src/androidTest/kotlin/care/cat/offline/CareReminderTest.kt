package care.cat.offline

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses the production database name on a disposable emulator to exercise real notifications. */
@RunWith(AndroidJUnit4::class)
class CareReminderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: CareStore
    private val manager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setup() {
        context.deleteDatabase("cat-care.db")
        store = CareStore(context)
        CareReminders.preferences(context).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33)
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
    }

    @After
    fun cleanup() {
        CareReminders.preferences(context).edit().putBoolean("enabled", false).commit()
        CareReminders.sync(context)
        store.close()
        context.deleteDatabase("cat-care.db")
    }

    @Test
    fun nearAndDueDeliverOnceAndMutationsCancelStaleNotifications() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        val cat = store.saveCat(null, "Mimi", "", "", "calico")
        store.saveTask(cat, null, "Exam", "Outro", now.plusSeconds(1800).toString(), 0, 0)
        CareReminders.sync(context, now)
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(
            manager.activeNotifications
                .single()
                .notification
                .extras
                .getCharSequence("android.bigText")
                .toString()
                .contains("Exam")
        )
        assertEquals(1, CareReminders.preferences(context).getStringSet("sent", emptySet())!!.size)
        CareReminders.sync(context, now.plusSeconds(10))
        assertEquals(1, CareReminders.preferences(context).getStringSet("sent", emptySet())!!.size)
        CareReminders.sync(context, now.plusSeconds(1800))
        assertEquals(2, CareReminders.preferences(context).getStringSet("sent", emptySet())!!.size)
        // Near and due update the same card, rather than leaving duplicate notifications.
        assertEquals(1, manager.activeNotifications.size)
        val task = store.tasks(cat).single()
        store.saveTask(cat, task, "Exam moved", "Outro", now.plusSeconds(86400).toString(), 0, 0)
        CareReminders.sync(context, now)
        assertEquals(0, manager.activeNotifications.size)
        assertTrue(CareReminders.preferences(context).getStringSet("sent", emptySet())!!.isEmpty())
        store.transition(store.tasks(cat).single(), "cancelled", "")
        CareReminders.sync(context, now.plusSeconds(86400))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun overdueSkipsAdvanceNoticeAndDisabledRemindersStaySilent() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        val cat = store.saveCat(null, "Luna", "", "", "siamese")
        store.saveTask(cat, null, "Water", "Outro", now.minusSeconds(30).toString(), 0, 0)
        CareReminders.preferences(context).edit().putBoolean("enabled", false).commit()
        CareReminders.sync(context, now)
        assertEquals(0, manager.activeNotifications.size)
        CareReminders.preferences(context).edit().putBoolean("enabled", true).commit()
        CareReminders.sync(context, now)
        assertEquals(1, manager.activeNotifications.size)
        val sent = CareReminders.preferences(context).getStringSet("sent", emptySet())!!
        assertEquals(1, sent.size)
        assertTrue(sent.single().endsWith("|due"))
        store.delete(cat)
        CareReminders.sync(context, now)
        assertEquals(0, manager.activeNotifications.size)
    }
}
