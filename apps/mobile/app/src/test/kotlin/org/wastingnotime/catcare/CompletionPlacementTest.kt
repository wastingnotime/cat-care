package org.wastingnotime.catcare

import org.junit.Assert.*
import org.junit.Test

class CompletionPlacementTest {
    private val task = CareStore.Task("care", "cat", "Brush", "hygiene", "", "planned", 0, 0, 1)
    private val before = CareSnapshot(tasks = listOf(task), loaded = true)
    private val completed = before.copy(
        tasks = listOf(task.copy(state = "completed", version = 2)), completedTaskId = task.id,
    )

    @Test fun onlyCommittedCompletionRefreshEnablesPlacement() {
        assertTrue(isCompletionRefresh(before, completed))
        assertFalse(isCompletionRefresh(CareSnapshot(), completed))
        assertFalse(isCompletionRefresh(completed, completed))
        assertFalse(isCompletionRefresh(before, completed.copy(completedTaskId = null)))
        assertFalse(isCompletionRefresh(before, before.copy(completedTaskId = task.id)))
        assertFalse(isCompletionRefresh(before, completed.copy(tasks = listOf(task.copy(state = "cancelled")))))
        assertFalse(isCompletionRefresh(before, completed.copy(completedTaskId = "other")))
    }
}
