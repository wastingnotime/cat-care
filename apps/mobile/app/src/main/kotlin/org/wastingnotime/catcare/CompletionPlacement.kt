package org.wastingnotime.catcare

/** Animate only a newly observed committed completion, never loading or other mutations. */
internal fun isCompletionRefresh(before: CareSnapshot, after: CareSnapshot): Boolean {
    val id = after.completedTaskId ?: return false
    return before.loaded && before != after &&
        before.tasks.any { it.id == id && it.state == "planned" } &&
        after.tasks.any { it.id == id && it.state == "completed" }
}
