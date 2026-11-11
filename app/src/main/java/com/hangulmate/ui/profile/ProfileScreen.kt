package com.hangulmate.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.BuildConfig
import com.hangulmate.R
import com.hangulmate.brand.BrandConfig
import com.hangulmate.domain.model.ImportReport
import com.hangulmate.locale.LocaleManager
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Fully functional settings screen, backed by SettingsRepository.
 *
 * Also hosts the real statistics (from the learning repositories),
 * backup & restore (local only, via [com.hangulmate.data.backup.BackupManager]),
 * the My Collections entry point and Reset Progress.
 */
@Composable
fun ProfileScreen(
    onNavigateToCollections: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val dailyGoal by viewModel.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val interfaceLanguage by viewModel.interfaceLanguage.collectAsStateWithLifecycle(initialValue = "ne")
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = "system")
    val ttsRate by viewModel.ttsRate.collectAsStateWithLifecycle(initialValue = 1.0f)
    val ttsPitch by viewModel.ttsPitch.collectAsStateWithLifecycle(initialValue = 1.0f)
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle(initialValue = false)
    val reminderHour by viewModel.reminderHour.collectAsStateWithLifecycle(initialValue = 19)
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val localBackupInfo by viewModel.localBackupInfo.collectAsStateWithLifecycle()

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<ImportReport?>(null) }
    var importFailed by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setRemindersEnabled(true)
        } else {
            // Denied: explain why the permission matters and send the user
            // to system settings. Reminders stay off until it is granted.
            showPermissionRationale = true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importFailed = false
                backupStatus = if (viewModel.exportToUri(uri)) {
                    context.getString(R.string.backup_export_done)
                } else {
                    context.getString(R.string.backup_export_failed)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val report = viewModel.importFromUri(uri)
                if (report == null) {
                    importFailed = true
                    backupStatus = context.getString(R.string.backup_import_failed)
                } else {
                    importFailed = false
                    importReport = report
                }
            }
        }
    }

    var rateSlider by remember { mutableFloatStateOf(ttsRate) }
    var pitchSlider by remember { mutableFloatStateOf(ttsPitch) }
    LaunchedEffect(ttsRate) { rateSlider = ttsRate }
    LaunchedEffect(ttsPitch) { pitchSlider = ttsPitch }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        // Daily goal stepper
        SettingsCard(title = stringResource(R.string.settings_daily_goal)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.setDailyGoal(dailyGoal - 1) },
                    enabled = dailyGoal > 1,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
                }
                Text(
                    text = stringResource(R.string.settings_daily_goal_value, dailyGoal),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { viewModel.setDailyGoal(dailyGoal + 1) },
                    enabled = dailyGoal < 50,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
                }
            }
        }

        // Language selector
        SettingsCard(title = stringResource(R.string.settings_language)) {
            Column(Modifier.selectableGroup()) {
                LanguageOption(
                    label = stringResource(R.string.language_nepali),
                    selected = interfaceLanguage == "ne",
                    onClick = {
                        viewModel.setInterfaceLanguage("ne")
                        LocaleManager.applyLanguage(context, "ne")
                        activity?.let { LocaleManager.recreateActivity(it) }
                    },
                )
                LanguageOption(
                    label = stringResource(R.string.language_english),
                    selected = interfaceLanguage == "en",
                    onClick = {
                        viewModel.setInterfaceLanguage("en")
                        LocaleManager.applyLanguage(context, "en")
                        activity?.let { LocaleManager.recreateActivity(it) }
                    },
                )
            }
        }

        // Theme selector
        SettingsCard(title = stringResource(R.string.settings_theme)) {
            Column(Modifier.selectableGroup()) {
                ThemeOption(
                    label = stringResource(R.string.theme_system),
                    selected = themeMode == "system",
                    onClick = { viewModel.setThemeMode("system") },
                )
                ThemeOption(
                    label = stringResource(R.string.theme_light),
                    selected = themeMode == "light",
                    onClick = { viewModel.setThemeMode("light") },
                )
                ThemeOption(
                    label = stringResource(R.string.theme_dark),
                    selected = themeMode == "dark",
                    onClick = { viewModel.setThemeMode("dark") },
                )
            }
        }

        // TTS
        SettingsCard(title = stringResource(R.string.settings_tts)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_tts_rate, rateSlider))
                Slider(
                    value = rateSlider,
                    onValueChange = {
                        rateSlider = it
                        viewModel.previewTtsRate(it)
                    },
                    onValueChangeFinished = { viewModel.setTtsRate(rateSlider) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
                Text(stringResource(R.string.settings_tts_pitch, pitchSlider))
                Slider(
                    value = pitchSlider,
                    onValueChange = {
                        pitchSlider = it
                        viewModel.previewTtsPitch(it)
                    },
                    onValueChangeFinished = { viewModel.setTtsPitch(pitchSlider) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
                when (ttsState) {
                    TtsState.Ready -> {
                        Button(
                            onClick = { viewModel.testVoice() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_tts_test_voice))
                        }
                    }
                    TtsState.MissingVoice -> {
                        Text(
                            text = stringResource(R.string.tts_missing_voice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is TtsState.Error -> {
                        Text(
                            text = stringResource(R.string.tts_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TtsState.Checking -> {
                        Text(
                            text = stringResource(R.string.tts_checking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Reminders
        SettingsCard(title = stringResource(R.string.settings_reminders)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_reminders_enable))
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                viewModel.setRemindersEnabled(false)
                            } else if (viewModel.hasNotificationPermission()) {
                                viewModel.setRemindersEnabled(true)
                            } else {
                                // Ask once; a denial opens the rationale
                                // dialog (with a link to system settings)
                                // instead of silently enabling reminders.
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.setReminderHour(reminderHour - 1) },
                        enabled = remindersEnabled && reminderHour > 0,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
                    }
                    Text(
                        text = stringResource(R.string.settings_reminder_hour, reminderHour),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { viewModel.setReminderHour(reminderHour + 1) },
                        enabled = remindersEnabled && reminderHour < 23,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
                    }
                }
                Text(
                    text = stringResource(R.string.settings_reminders_note_live),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Statistics — real counts from the repositories (zero is a real
        // value: a fresh user sees the empty state, not invented numbers).
        SettingsCard(title = stringResource(R.string.settings_statistics)) {
            val hasProgress = stats.streak > 0 || stats.learned > 0 ||
                stats.mastered > 0 || stats.quizzesTaken > 0
            if (!hasProgress) {
                Text(
                    text = stringResource(R.string.statistics_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatRow(
                        label = stringResource(R.string.stats_streak),
                        value = stringResource(R.string.stats_streak_days, stats.streak),
                    )
                    StatRow(
                        label = stringResource(R.string.stats_learned),
                        value = stats.learned.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.stats_mastered),
                        value = stats.mastered.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.stats_total_words),
                        value = stats.totalWords.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.stats_quizzes),
                        value = stats.quizzesTaken.toString(),
                    )
                }
            }
        }

        // My Collections
        Button(
            onClick = onNavigateToCollections,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Text(
                text = stringResource(R.string.collections_my_collections),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        // Backup & restore — all local, no network, no login.
        SettingsCard(title = stringResource(R.string.backup_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.backup_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("hangulmate-backup-${LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_import))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            importFailed = false
                            if (viewModel.writeLocalBackup()) {
                                backupStatus = context.getString(R.string.backup_export_done)
                            } else {
                                backupStatus = context.getString(R.string.backup_export_failed)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_save_local))
                }
                val backupInfo = localBackupInfo
                Text(
                    text = if (backupInfo == null) {
                        stringResource(R.string.backup_local_none)
                    } else {
                        stringResource(R.string.backup_local_saved, backupInfo.fileName)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                backupStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (importFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        // Reset progress — destructive, always behind a confirmation dialog.
        SettingsCard(title = stringResource(R.string.reset_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.reset_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (resetDone) {
                    Text(
                        text = stringResource(R.string.reset_done),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // About
        SettingsCard(title = stringResource(R.string.settings_about)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = BrandConfig.appName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.offline_notice),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Notification permission rationale: shown when the user denied the
    // permission. Offers system settings; reminders stay off until granted.
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.reminder_permission_title)) },
            text = { Text(stringResource(R.string.reminder_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        ).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                ) {
                    Text(stringResource(R.string.reminder_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Reset progress confirmation. The dialog text states explicitly that
    // vocabulary words (including user-added ones) are NOT deleted.
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.resetProgress()
                            resetDone = true
                            showResetDialog = false
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.reset_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Import result: added / skipped / error counts, plus first error reasons.
    importReport?.let { report ->
        AlertDialog(
            onDismissRequest = { importReport = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(
                            R.string.backup_import_summary,
                            report.added,
                            report.skipped,
                            report.errors
                        ),
                    )
                    if (report.errorMessages.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.backup_import_errors,
                                report.errorMessages.joinToString("\n")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importReport = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
