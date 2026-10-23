package care.cat.offline

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Resets app data; run only on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class CareComposeTest {
    private lateinit var store: CareStore
    private lateinit var mimi: String
    private lateinit var luna: String
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture =
        object : ExternalResource() {
            override fun before() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.deleteDatabase("cat-care.db")
                context.getSharedPreferences("MainActivity", 0).edit().clear().commit()
                store = CareStore(context)
                mimi = store.saveCat(null, "Mimi", "", "", "calico")
                luna = store.saveCat(null, "Luna", "", "", "siamese")
                store.saveTask(mimi, null, "Água da Mimi", "Outro", "", 0, 0)
                store.saveTask(luna, null, "Ração da Luna", "Outro", "", 0, 0)
                store.record(mimi, "observation", "Mimi brincou")
                store.record(luna, "measurement", "Luna pesa 4 kg")
            }

            override fun after() {
                store.close()
            }
        }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun ready() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("+ Planejar cuidado").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
    }

    private fun save() {
        compose.onNodeWithTag("save-editor").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("save-editor").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun field(key: String, value: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performTextReplacement(value)
    }

    private fun nav(route: String) {
        compose.onNodeWithTag("nav-$route").performClick()
    }

    @Test
    fun todayPlansForAvatarChoiceAndRestoresDraft() {
        ready()
        compose.onNodeWithText("CUIDANDO DE").assertDoesNotExist()
        compose.onNodeWithText("Registrar").assertDoesNotExist()
        compose.onNodeWithText("Mimi brincou").assertDoesNotExist()
        click("+ Planejar cuidado")
        compose.onNodeWithTag("avatar-$luna").assertIsNotSelected()
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("title", "Escovar Mimi")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-$mimi").assertIsSelected()
        compose.onNodeWithTag("field-title").assertTextContains("Escovar Mimi")
        save()
        assertTrue(store.tasks(mimi).any { it.title == "Escovar Mimi" })
        assertFalse(store.tasks(luna).any { it.title == "Escovar Mimi" })
    }

    @Test
    fun newPlanRequiresChoiceAndPreselectsOnlyCat() {
        ready()
        click("+ Planejar cuidado")
        field("title", "Escolha o gato")
        compose.onNodeWithTag("save-editor").performClick()
        compose.onNodeWithTag("validation-error").assertTextContains("Informe o gato.")
        assertEquals(2, store.tasks(null).size)
        click("Voltar")
        nav("data")
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Apagar dados de Luna"))
        click("Apagar dados de Luna")
        save()
        nav("today")
        click("+ Planejar cuidado")
        compose.onNodeWithTag("avatar-$mimi").assertIsSelected()
        field("title", "Só Mimi")
        save()
        assertTrue(store.tasks(mimi).any { it.title == "Só Mimi" })
    }

    @Test
    fun historyRegistersAndCommentsWithRestoredDraft() {
        ready()
        nav("history")
        compose.onNodeWithText("Comentar").assertDoesNotExist()
        click("Registrar cuidado ou observação")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("description", "Registro da Mimi")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-$mimi").assertIsSelected()
        save()
        compose.onNodeWithText("Registro da Mimi").assertExists()
        val comment = hasContentDescription("Comentar observação: Mimi brincou")
        compose.onNodeWithTag("care-list").performScrollToNode(comment)
        compose.onNode(comment).performClick()
        field("description", "Brincou à tarde")
        compose.activityRule.scenario.recreate()
        save()
        val observation = store.history(mimi).first { it.type == "observation" }
        assertEquals("Brincou à tarde", store.comments(observation.id).single().description)
        compose.onNodeWithText("Brincou à tarde").assertExists()
        nav("today")
        compose.onNodeWithText("Brincou à tarde").assertDoesNotExist()
        nav("history")
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Brincou à tarde"))
        compose.onNodeWithText("Brincou à tarde").assertIsDisplayed()
    }

    @Test
    fun cardsCreateEditAndRestoreCoat() {
        ready()
        nav("cats")
        compose.onNodeWithContentDescription("Pelagem Siamês").assertExists()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Editar perfil de Mimi"))
        compose.onNodeWithText("1 observação").assertExists()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("+ Adicionar gato"))
        click("+ Adicionar gato")
        field("name", "Nina")
        compose.onNodeWithTag("avatar-tuxedo").performScrollTo().performClick()
        save()
        compose.onNodeWithText("Editar perfil de Nina").assertExists()
        assertEquals("tuxedo", store.cats().single { it.name == "Nina" }.coat)
        click("Editar perfil de Nina")
        field("name", "Nina Nova")
        compose.onNodeWithTag("avatar-calico").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-calico").performScrollTo().assertIsSelected()
        save()
        val edited = store.cats().single { it.name == "Nina Nova" }
        assertEquals("calico", edited.coat)
        assertEquals(3, store.cats().size)
        compose.onNodeWithText("Editar perfil de Nina Nova").assertIsDisplayed()
    }

    @Test
    fun taskCompletionKeepsConfirmationAndHistory() {
        ready()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Ver ações · Água da Mimi"))
        click("Ver ações · Água da Mimi")
        click("Concluir")
        compose.onNodeWithText("Concluir cuidado?").assertExists()
        assertEquals("planned", store.tasks(mimi).single().state)
        save()
        assertEquals("completed", store.tasks(mimi).single().state)
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Concluído: Água da Mimi"))
        compose.onNodeWithText("Concluído: Água da Mimi").assertIsDisplayed()
    }

    @Test
    fun dataScopesExportAndConfirmsDeletion() {
        ready()
        nav("data")
        click("Exportar Luna")
        compose.onNodeWithText("Gatos: Luna").assertExists()
        click("Voltar")
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Apagar dados de Luna"))
        click("Apagar dados de Luna")
        compose.onNodeWithText("Apagar Luna?").assertExists()
        assertEquals(2, store.cats().size)
        save()
        assertEquals(listOf("Mimi"), store.cats().map { it.name })
        assertTrue(store.history(luna).isEmpty())
    }
}
