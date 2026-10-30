package org.wastingnotime.catcare

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
internal fun BackupSettings(model: CareViewModel, busy: Boolean) {
    val context = LocalContext.current
    val backups = remember { CareBackups(context.applicationContext) }
    var status by remember { mutableStateOf(backups.status()) }
    DisposableEffect(backups) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> status = backups.status() }
        backups.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { backups.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val folder =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
            model::configureBackup,
        )
    val restore =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
            model::prepareRestore,
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(R.string.backup_restore), style = MaterialTheme.typography.titleLarge)
        Text(tr(if (status.enabled) R.string.backup_on_short else R.string.backup_off_short))
        Text(
            if (status.lastSuccess == 0L) tr(R.string.backup_never)
            else
                tr(
                    R.string.backup_last,
                    historyTime(Instant.ofEpochMilli(status.lastSuccess).toString()),
                ),
            modifier = Modifier.testTag("last-backup"),
        )
        var details by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(tr(R.string.backup_details)) }
        if (details) Text(tr(R.string.backup_description), color = Muted)
        if (status.failed) Text(tr(R.string.backup_error), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                try {
                    folder.launch(null)
                } catch (_: android.content.ActivityNotFoundException) {
                    model.field("kind", "export-unavailable")
                }
            },
            enabled = !busy,
            modifier = Modifier.testTag("choose-backup-folder"),
        ) {
            Text(
                tr(
                    if (status.enabled) R.string.backup_change_folder
                    else R.string.backup_choose_folder
                )
            )
        }
        if (status.enabled) {
            OutlinedButton(
                onClick = model::backupNow,
                enabled = !busy,
                modifier = Modifier.testTag("backup-now"),
            ) {
                Text(tr(R.string.backup_now))
            }
            TextButton(onClick = model::disableBackup, enabled = !busy) {
                Text(tr(R.string.backup_disable))
            }
        }
        OutlinedButton(
            onClick = {
                try {
                    restore.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain")
                    )
                } catch (_: android.content.ActivityNotFoundException) {
                    model.field("kind", "export-unavailable")
                }
            },
            enabled = !busy,
            modifier = Modifier.testTag("restore-backup"),
        ) {
            Text(tr(R.string.restore_action))
        }
    }
}
