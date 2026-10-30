package org.wastingnotime.catcare

import android.content.ContentValues
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject

/** A validated full-data snapshot. Scoped exports are deliberately not replacement backups. */
internal class CareBackupDocument private constructor(val tables: Map<String, List<ContentValues>>) {
    fun count(table: String) = tables.getValue(table).size

    companion object {
        const val MAX_BYTES = 16 * 1024 * 1024

        fun parse(text: String): CareBackupDocument {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = JSONObject(text)
            require(root.getString("format") == "cat-care-offline")
            require(root.get("version") == 3)
            require(root.getString("scope") == "all_local_data")
            Instant.parse(root.getString("exported_at"))
            val columns = linkedMapOf(
                "cats" to listOf("id", "name", "birth", "adoption", "coat"),
                "tasks" to listOf("id", "cat_id", "title", "category", "due", "state", "days", "months", "version", "zone"),
                "history" to listOf("id", "cat_id", "task_id", "type", "description", "at", "is_diagnosis", "details"),
                "observation_comments" to listOf("id", "observation_id", "description", "at"),
            )
            val integers = setOf("days", "months", "version", "is_diagnosis")
            val tables = columns.mapValues { (table, fields) ->
                val rows = root.getJSONArray(table)
                val ids = mutableSetOf<String>()
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    val values = ContentValues()
                    fields.forEach { field ->
                        val value = when {
                            field == "zone" && !row.has(field) -> "UTC"
                            field == "details" && !row.has(field) -> JSONObject.NULL
                            else -> row.get(field)
                        }
                        when {
                            value == JSONObject.NULL -> {
                                require(field == "task_id" || field == "details")
                                values.putNull(field)
                            }
                            field in integers -> {
                                require(value is Int || value is Long)
                                val number = (value as Number).toLong()
                                require(number in 0..Int.MAX_VALUE.toLong())
                                values.put(field, number.toInt())
                            }
                            else -> { require(value is String); values.put(field, value) }
                        }
                    }
                    fun str(key: String) = values.getAsString(key)
                    require(str("id").isNotBlank() && ids.add(str("id")))
                    when (table) {
                        "cats" -> {
                            CareRules.profile(str("name"), str("birth"), str("adoption"))
                            CatCoat.fromId(str("coat"))
                        }
                        "tasks" -> {
                            require(str("title").isNotBlank() && str("category") in Categories)
                            require(str("state") in listOf("planned", "completed", "cancelled"))
                            CareRules.recurrence(values.getAsInteger("days"), values.getAsInteger("months"))
                            val due = str("due")
                            if (due.isNotEmpty()) {
                                val parsed = ZonedDateTime.parse(due)
                                values.put("due", parsed.toInstant().toString())
                                if (!row.has("zone")) values.put("zone", parsed.zone.id)
                            } else require(values.getAsInteger("days") == 0 && values.getAsInteger("months") == 0)
                            ZoneId.of(str("zone"))
                        }
                        "history" -> {
                            require(str("type") in RecordTypes + listOf("profile", "created", "edited", "completed", "cancelled", "deferred"))
                            require(str("description").isNotBlank() && values.getAsInteger("is_diagnosis") == 0)
                            Instant.parse(str("at"))
                            values.getAsString("details")?.let { raw ->
                                val details = JSONObject(raw)
                                listOf("due", "from_due").forEach { key ->
                                    if (details.optString(key).isNotEmpty()) details.put(key, ZonedDateTime.parse(details.getString(key)).toInstant().toString())
                                }
                                values.put("details", details.toString())
                            }
                        }
                        "observation_comments" -> {
                            require(str("description").isNotBlank())
                            Instant.parse(str("at"))
                        }
                    }
                    values
                }
            }
            val cats = tables.getValue("cats").map { it.getAsString("id") }.toSet()
            val tasks = tables.getValue("tasks").associate { it.getAsString("id") to it.getAsString("cat_id") }
            val history = tables.getValue("history").map { it.getAsString("id") }.toSet()
            tables.getValue("tasks").forEach { require(it.getAsString("cat_id") in cats) }
            tables.getValue("history").forEach {
                val cat = it.getAsString("cat_id")
                require(cat in cats)
                it.getAsString("task_id")?.let { task -> require(tasks[task] == cat) }
            }
            tables.getValue("observation_comments").forEach { require(it.getAsString("observation_id") in history) }
            return CareBackupDocument(tables)
        }
    }
}
