package org.wastingnotime.catcare

import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
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
                context
                    .getSharedPreferences("appearance", 0)
                    .edit()
                    .putString("language", "pt-BR")
                    .putString("theme", "light")
                    .commit()
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    context
                        .getSystemService(android.app.LocaleManager::class.java)
                        .applicationLocales = android.os.LocaleList.forLanguageTags("pt-BR")
                context
                    .getSharedPreferences("reminders", 0)
                    .edit()
                    .putBoolean("enabled", false)
                    .commit()
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
        if (route in listOf("data", "settings", "about")) {
            compose.onNodeWithTag("more-options").performClick()
            compose.onNodeWithTag("menu-$route").performClick()
        } else {
            repeat(8) {
                if (compose.onAllNodesWithTag("nav-$route").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("nav-$route").performClick()
                    return
                }
                compose.onNodeWithTag("navigate-up").performClick()
            }
        }
    }

    private fun openEntry(description: String): String {
        val entry =
            store.history(mimi).plus(store.history(luna)).first { it.description == description }
        compose.onNodeWithTag("care-list").performScrollToNode(hasTestTag("entry-${entry.id}"))
        compose
            .onNodeWithTag("entry-${entry.id}")
            .performTouchInput { click(Offset(24f, 24f)) }
        return entry.id
    }

    @Test
    fun overflowSeparatesDataSettingsAndPrivacyFromThreeLabeledTabs() {
        ready()
        compose.onNodeWithTag("nav-data").assertDoesNotExist()
        compose.onNodeWithTag("nav-today").assertTextContains("Hoje")
        compose.onNodeWithTag("nav-cats").assertTextContains("Gatos")
        compose.onNodeWithTag("nav-history").assertTextContains("Histórico")
        compose.onNodeWithTag("today-date").assertExists()
        nav("settings")
        compose.onNodeWithText("Idioma").assertExists()
        compose.onNodeWithTag("choose-backup-folder").assertDoesNotExist()
        nav("data")
        compose.onNodeWithTag("choose-backup-folder").assertExists()
        compose.onNodeWithText("Idioma").assertDoesNotExist()
        nav("about")
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Privacidade"))
        compose.onNodeWithText("Privacidade").assertExists()
        compose
            .onNodeWithTag("care-list")
            .performScrollToNode(hasText("A Wasting No Time (WNT) desenvolve o Cat Care."))
        compose.onNodeWithText("A Wasting No Time (WNT) desenvolve o Cat Care.").assertExists()
    }

    @Test
    fun restorePreviewSurvivesRecreationAndRequiresConfirmation() {
        ready()
        val snapshot = store.export(null)
        val file =
            java.io.File(compose.activity.cacheDir, "restore-test.json").apply {
                writeText(snapshot)
            }
        store.saveCat(null, "After backup", "", "", "gray")
        compose.activityRule.scenario.recreate()
        ready()
        nav("data")
        compose.runOnIdle {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[CareViewModel::class.java]
                .prepareRestore(android.net.Uri.fromFile(file))
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("confirm-restore").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(3, store.cats().size)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("confirm-restore").assertExists()
        compose.onNodeWithTag("close-editor").performClick()
        assertEquals(3, store.cats().size)
        compose.runOnIdle {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[CareViewModel::class.java]
                .prepareRestore(android.net.Uri.fromFile(file))
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("confirm-restore").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("confirm-restore").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("confirm-restore").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(setOf("Mimi", "Luna"), store.cats().map { it.name }.toSet())
        file.delete()
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
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("discard-changes").performClick()
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
        compose.onAllNodesWithText("Comentar").assertCountEquals(0)
        click("Registrar cuidado ou observação")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("description", "Registro da Mimi")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-$mimi").assertIsSelected()
        save()
        compose.onNodeWithText("Registro da Mimi").assertExists()
        // Let the save snackbar leave the touch area before opening the comment form.
        compose.mainClock.advanceTimeBy(5000)
        nav("history")
        val entryId = openEntry("Mimi brincou")
        compose.onNodeWithTag("comment-$entryId").performClick()
        field("description", "Brincou à tarde")
        compose.activityRule.scenario.recreate()
        save()
        val observation = store.history(mimi).first { it.type == "observation" }
        assertEquals("Brincou à tarde", store.comments(observation.id).single().description)
        compose.onNodeWithText("Brincou à tarde").assertExists()
        nav("today")
        compose.onNodeWithText("Brincou à tarde").assertDoesNotExist()
        nav("history")
        openEntry("Mimi brincou")
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Brincou à tarde"))
        compose.onNodeWithText("Brincou à tarde").assertIsDisplayed()
    }

    @Test
    fun cardsCreateEditAndRestoreCoat() {
        ready()
        nav("cats")
        compose.onNodeWithContentDescription("Pelagem Siamês").assertExists()
        compose.onNodeWithText("1 observação").assertDoesNotExist()
        click("+ Adicionar gato")
        field("name", "Nina")
        compose.onNodeWithTag("avatar-tuxedo").performScrollTo().performClick()
        save()
        val nina = store.cats().single { it.name == "Nina" }
        compose.onNodeWithTag("cat-card-${nina.id}").assertExists()
        assertEquals("tuxedo", nina.coat)
        compose.onNodeWithTag("cat-menu-${nina.id}").performClick()
        click("Editar perfil")
        field("name", "Nina Nova")
        compose.onNodeWithTag("avatar-calico").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-calico").performScrollTo().assertIsSelected()
        save()
        val edited = store.cats().single { it.name == "Nina Nova" }
        assertEquals("calico", edited.coat)
        assertEquals(3, store.cats().size)
        compose.onNodeWithTag("cat-card-${edited.id}").assertIsDisplayed()
    }

    @Test
    fun taskCompletionKeepsConfirmationAndHistory() {
        ready()
        val task = store.tasks(mimi).single()
        compose.onNodeWithTag("care-list").performScrollToNode(hasTestTag("complete-${task.id}"))
        compose.onNodeWithTag("complete-${task.id}").performClick()
        compose.onNodeWithText("Concluir cuidado?").assertExists()
        assertEquals("planned", store.tasks(mimi).single().state)
        save()
        assertEquals("completed", store.tasks(mimi).single().state)
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Concluído: Água da Mimi", substring = true))
        compose.onNodeWithText("Concluído: Água da Mimi", substring = true).assertIsDisplayed()
    }

    @Test
    fun careAndHistoryCardsExposeTheirCatBadges() {
        ready()
        compose.onNodeWithTag("care-list").performScrollToNode(hasTestTag("cat-badge-$mimi"))
        compose.onNodeWithTag("cat-badge-$mimi").assertContentDescriptionEquals("Gato: Mimi")
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        compose.onAllNodesWithTag("cat-badge-$mimi").onFirst().assertExists()
    }

    @Test
    fun localeChoiceSwitchesLabelsAndLeavesOwnerTextUnchanged() {
        ready()
        nav("settings")
        click("Português (Brasil)")
        click("English (US)")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Language").assertExists()
        nav("today")
        click("+ Plan care")
        field("title", "Owner text stays exactly the same")
        compose.onNodeWithTag("save-editor").performClick()
        compose.onNodeWithTag("validation-error").assertTextContains("Choose a cat.")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        save()
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        compose
            .onNodeWithTag("care-list")
            .performScrollToNode(hasText("Care planned: Owner text stays exactly the same", substring = true))
        compose.onNodeWithText("Care planned: Owner text stays exactly the same", substring = true).assertExists()
        nav("settings")
        click("English (US)")
        click("Português (Brasil)")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Idioma").fetchSemanticsNodes().isNotEmpty()
        }
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        compose
            .onNodeWithTag("care-list")
            .performScrollToNode(hasText("Cuidado criado: Owner text stays exactly the same", substring = true))
        compose.onNodeWithText("Cuidado criado: Owner text stays exactly the same", substring = true).assertExists()
    }

    @Test
    fun categorySuggestsTitleUntilCustomizedAndSurvivesRecreation() {
        ready()
        click("+ Planejar cuidado")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        compose.onNodeWithTag("field-title").performScrollTo().assertTextContains("Alimentação")
        compose.onNodeWithTag("category-Higiene").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("field-title").performScrollTo().assertTextContains("Higiene")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("category-Medicação").performScrollTo().performClick()
        compose.onNodeWithTag("field-title").performScrollTo().assertTextContains("Medicação")
        field("title", "Remédio da Mimi")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("category-Outro").performScrollTo().performClick()
        compose.onNodeWithTag("field-title").performScrollTo().assertTextContains("Remédio da Mimi")
        save()
        assertTrue(store.tasks(mimi).any { it.title == "Remédio da Mimi" && it.category == "Outro" })
    }

    @Test
    fun calendarTimeAndWeeklyRepeatSurviveRecreation() {
        ready()
        click("+ Planejar cuidado")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("title", "Cuidado semanal")
        compose.onNodeWithTag("due-date").performScrollTo().performClick()
        // The Today header has the same full date, so select only inside the calendar dialog.
        val today = java.time.LocalDate.now()
        val dateLabel =
            today.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "EEEE, d 'de' MMMM 'de' uuuu",
                    java.util.Locale("pt", "BR"),
                )
            )
        val day =
            compose.onAllNodes(
                (hasText(dateLabel, substring = true) or
                    hasContentDescription(dateLabel, substring = true)) and
                    hasAnyAncestor(isDialog()),
                useUnmergedTree = true,
            )
        day.onFirst().performClick()
        click("Confirmar")
        compose
            .onNodeWithTag("due-time")
            .performScrollTo()
            .assertIsEnabled()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("toggle-time-input").assertTextContains("Digitar horário")
        compose.onNodeWithTag("toggle-time-input").performScrollTo().performClick()
        val inputs =
            compose.onAllNodes(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("time-dialog")),
                useUnmergedTree = true,
            )
        inputs[0].performTextReplacement("09")
        inputs.onLast().performTextReplacement("30")
        compose.onNodeWithTag("toggle-time-input").performScrollTo().performClick()
        compose.onNodeWithTag("toggle-time-input").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-time").performClick()
        compose.onNodeWithText("Toda semana").performScrollTo().performClick()
        compose
            .onNodeWithTag("repeat-preview")
            .performScrollTo()
            .assertTextContains("Próximo cuidado:", substring = true)
        compose.activityRule.scenario.recreate()
        save()
        val task = store.tasks(mimi).single { it.title == "Cuidado semanal" }
        assertEquals(7, task.days)
        assertEquals(0, task.months)
        assertEquals(
            java.time.LocalTime.of(9, 30),
            java.time.ZonedDateTime.parse(task.due)
                .withZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalTime(),
        )
        assertEquals(
            today,
            java.time.ZonedDateTime.parse(task.due)
                .withZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDate(),
        )
    }

    @Test
    fun dataScopesExportAndConfirmsDeletion() {
        ready()
        nav("data")
        compose.onNodeWithText("Exportar Luna").performScrollTo().performClick()
        compose.onNodeWithText("Gatos: Luna").assertExists()
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Apagar dados de Luna"))
        click("Apagar dados de Luna")
        compose.onNodeWithText("Apagar Luna?").assertExists()
        assertEquals(2, store.cats().size)
        save()
        assertEquals(listOf("Mimi"), store.cats().map { it.name })
        assertTrue(store.history(luna).isEmpty())
    }

    @Test
    fun historyBadgeFiltersAndMeasurementCommentSharesOnlyThisCard() {
        ready()
        nav("history")
        compose.onNodeWithTag("filter-$luna").performClick()
        compose.onNodeWithTag("filter-$luna").assertIsSelected()
        compose.onNodeWithText("Mimi brincou").assertDoesNotExist()
        val entry = store.history(luna).first { it.type == "measurement" }
        openEntry("Luna pesa 4 kg")
        compose.onNodeWithTag("comment-${entry.id}").performClick()
        field("description", "Peso estável")
        save()
        assertEquals("Peso estável", store.comments(entry.id).single().description)
        compose.mainClock.advanceTimeBy(5000)
        var captured: android.content.Intent? = null
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val monitor =
            object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(
                    intent: android.content.Intent
                ): android.app.Instrumentation.ActivityResult? {
                    if (intent.action != android.content.Intent.ACTION_CHOOSER) return null
                    captured = intent
                    return android.app.Instrumentation.ActivityResult(0, null)
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            compose
                .onNodeWithTag("care-list")
                .performScrollToNode(hasTestTag("entry-menu-${entry.id}"))
            compose.onNodeWithTag("entry-menu-${entry.id}").performClick()
            click("Compartilhar cartão")
            compose.waitUntil(5000) { captured != null }
            @Suppress("DEPRECATION")
            val share =
                captured!!.getParcelableExtra<android.content.Intent>(
                    android.content.Intent.EXTRA_INTENT
                )!!
            assertEquals(android.content.Intent.ACTION_SEND, share.action)
            assertEquals("text/plain", share.type)
            val text = share.getStringExtra(android.content.Intent.EXTRA_TEXT)!!
            assertTrue(text.contains("Cat Care • Luna"))
            assertTrue(text.contains("Luna pesa 4 kg"))
            assertTrue(text.contains("• Peso estável"))
            assertFalse(text.contains("UTC"))
            assertFalse(text.contains(java.time.ZoneId.systemDefault().id))
            assertFalse(text.contains("Mimi"))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun darkThemeChoiceSurvivesActivityRecreation() {
        ready()
        nav("settings")
        compose.onNodeWithText("Aparência").performScrollTo()
        click("Claro")
        click("Escuro")
        assertEquals(
            "dark",
            compose.activity.getSharedPreferences("appearance", 0).getString("theme", ""),
        )
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Escuro").assertExists()
        nav("today")
        compose.onNodeWithTag("nav-today").assertTextContains("Hoje")
    }

    @Test
    fun overdueHeaderIncludesPastDueCare() {
        store.saveTask(
            mimi,
            null,
            "Exame atrasado",
            "Outro",
            java.time.Instant.now().minusSeconds(3600).toString(),
            0,
            0,
        )
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithText("Hoje: 0 · Em atraso: 1").assertIsDisplayed()
        compose.onNodeWithText("Atrasados").assertIsDisplayed()
        val task = store.tasks(mimi).first { it.title == "Exame atrasado" }
        compose.onNodeWithTag("care-list").performScrollToNode(hasTestTag("complete-${task.id}"))
        compose.onNodeWithTag("complete-${task.id}").assertIsDisplayed()
    }

    @Test
    fun importedCalendarDateNeverDisplaysAnInventedTimeOrShiftsWithUtcAnchor() {
        val entry = store.history(mimi).first { it.type == "observation" }
        store.writableDatabase.execSQL(
            "UPDATE history SET at=?,details=? WHERE id=?",
            arrayOf(
                "2023-11-18T00:00:00Z",
                org.json
                    .JSONObject()
                    .put("occurred_on", "2023-11-18")
                    .put("date_precision", "day")
                    .toString(),
                entry.id,
            ),
        )
        compose.activityRule.scenario.recreate()
        ready()
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        val date = "Data informada: 18/11/2023 (horário não informado)"
        compose.onNodeWithTag("care-list").performScrollToNode(hasText(date))
        compose.onNodeWithText(date).assertIsDisplayed()
        assertEquals("2023-11-18", historySourceDate(store.historyEntry(entry.id)!!.details))
        assertNull(historySourceDate("{broken"))
        assertNull(historySourceDate("{}"))
    }

    @Test
    fun changedDraftRequiresDiscardAndSurvivesRecreation() {
        ready()
        click("+ Planejar cuidado")
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("discard-changes").assertDoesNotExist()
        click("+ Planejar cuidado")
        field("title", "Keep this draft")
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("discard-changes").assertExists()
        compose.activityRule.scenario.recreate()
        click("Continuar editando")
        compose.onNodeWithTag("field-title").assertTextContains("Keep this draft")
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("discard-changes").performClick()
        compose.onNodeWithTag("save-editor").assertDoesNotExist()
        assertFalse(store.tasks(null).any { it.title == "Keep this draft" })
    }

    @Test
    fun childNavigationReturnsToOriginAndProfileProvidesCareContext() {
        ready()
        nav("cats")
        compose.onNodeWithTag("cat-card-$mimi").performClick()
        compose.onNodeWithText("Água da Mimi").assertExists()
        nav("settings")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("navigate-up").performClick()
        compose.onNodeWithTag("activity-title").assertTextContains("Perfil do gato")
        compose.onNodeWithTag("navigate-up").performClick()
        compose.onNodeWithTag("nav-cats").assertIsSelected()
        nav("history")
        nav("data")
        compose.onNodeWithTag("navigate-up").performClick()
        compose.onNodeWithTag("nav-history").assertIsSelected()
    }

    @Test
    fun notificationTargetsCareWithoutReplacingAnUnfinishedDraft() {
        ready()
        val task = store.tasks(mimi).single()
        click("+ Planejar cuidado")
        field("title", "Still editing")
        compose.runOnIdle {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[CareViewModel::class.java]
                .openNotificationTask(task.id)
        }
        compose.onNodeWithTag("field-title").assertTextContains("Still editing")
        compose.onNodeWithTag("close-editor").performClick()
        compose.onNodeWithTag("discard-changes").performClick()
        compose.onNodeWithText("Detalhes do cuidado").assertExists()
        compose.onNodeWithText("Mimi · Água da Mimi").assertExists()
    }

    @Test
    fun backdatedRecordOpensTheSavedEntryAndKeepsEntryTimeSeparate() {
        ready()
        nav("history")
        click("Registrar cuidado ou observação")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("description", "Observed yesterday")
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        compose.runOnIdle {
            val model =
                androidx.lifecycle.ViewModelProvider(compose.activity)[CareViewModel::class.java]
            model.field("dueDate", yesterday)
            model.field("dueTime", "09:00")
        }
        save()
        val entry = store.history(mimi).first { it.description == "Observed yesterday" }
        assertEquals(
            yesterday,
            java.time.Instant.parse(entry.at)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString(),
        )
        assertTrue(
            java.time.Instant.parse(org.json.JSONObject(entry.details!!).getString("recorded_at"))
                .isAfter(java.time.Instant.parse(entry.at))
        )
        compose.onNodeWithTag("activity-title").assertTextContains("Detalhes do registro")
        compose.onNodeWithText("Observed yesterday").assertExists()
        val before = store.export(null)
        store.restore(before)
        assertEquals(entry.at, store.historyEntry(entry.id)!!.at)
        assertEquals(entry.details, store.historyEntry(entry.id)!!.details)
    }

    @Test
    fun historyFiltersAndCollapsedUpcomingKeepListsFocused() {
        store.saveTask(
            mimi,
            null,
            "Future appointment",
            "Outro",
            java.time.Instant.now().plusSeconds(172800).toString(),
            0,
            0,
        )
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithText("Future appointment").assertDoesNotExist()
        compose.onNodeWithTag("toggle-upcoming").performClick()
        compose.onNodeWithTag("care-list").performScrollToNode(hasText("Future appointment"))
        compose.onNodeWithText("Future appointment").assertIsDisplayed()
        nav("history")
        compose.onNodeWithTag("filter-$mimi").performClick()
        click("Todos os tipos")
        compose.onNode(hasText("Observação (não é diagnóstico)") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Mimi brincou").assertExists()
        compose.onNodeWithText("Cuidado criado: Água da Mimi").assertDoesNotExist()
    }

    @Test
    fun capturesUsableScreensAndLargeTextEditor() {
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun capture(name: String) {
            compose.waitForIdle()
            android.os.SystemClock.sleep(300)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CatCareUX")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val resolver = compose.activity.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            bitmap.recycle()
        }
        ready()
        store.saveTask(
            mimi,
            null,
            "Escovar Mimi",
            "Higiene",
            java.time.Instant.now().minusSeconds(1800).toString(),
            0,
            0,
        )
        store.saveTask(
            luna,
            null,
            "Consulta de rotina",
            "Veterinário",
            java.time.Instant.now().plusSeconds(172800).toString(),
            0,
            0,
        )
        compose.activityRule.scenario.recreate()
        ready()
        capture("today")
        nav("cats")
        capture("cats")
        compose.onNodeWithTag("cat-card-$mimi").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithTag("activity-title")
                .fetchSemanticsNodes()
                .any { "Perfil do gato" in it.config.toString() }
        }
        compose.onNodeWithTag("activity-title").assertTextContains("Perfil do gato")
        capture("profile")
        nav("history")
        compose.onNodeWithTag("activity-title").assertTextContains("Histórico")
        compose.onNodeWithTag("filter-$mimi").performClick()
        capture("history")
        openEntry("Mimi brincou")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Detalhes do registro").fetchSemanticsNodes().isNotEmpty()
        }
        capture("entry")
        nav("data")
        compose.onNodeWithTag("activity-title").assertTextContains("Dados")
        compose.onNodeWithText("Backup e restauração").assertExists()
        capture("data")
        nav("today")
        click("+ Planejar cuidado")
        compose.onNodeWithTag("avatar-$mimi").performClick()
        field("title", "Escovar Mimi")
        compose.onNodeWithTag("field-title").assertTextContains("Escovar Mimi")
        capture("plan-editing")
        compose.onNodeWithTag("close-editor").performClick()
        capture("discard")
        compose.onNodeWithTag("discard-changes").performClick()
        nav("settings")
        click("Claro")
        click("Escuro")
        nav("today")
        capture("today-dark")
        nav("settings")
        click("Português (Brasil)")
        click("English (US)")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty()
        }
        nav("today")
        capture("today-en")
        try {
            instrumentation.uiAutomation
                .executeShellCommand("settings put system font_scale 2.0")
                .close()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) { compose.activity.resources.configuration.fontScale >= 2f }
            click("+ Plan care")
            compose.onNodeWithTag("avatar-$mimi").performClick()
            field("title", "Large text draft")
            compose.onNodeWithTag("save-editor").assertIsDisplayed()
            capture("plan-large-text")
            save()
            assertTrue(store.tasks(mimi).any { it.title == "Large text draft" })
        } finally {
            instrumentation.uiAutomation
                .executeShellCommand("settings put system font_scale 1.0")
                .close()
        }
    }
}
