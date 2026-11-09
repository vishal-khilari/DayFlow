@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value - step >= min) onChange(value - step) }) { Text("−", fontSize = 20.sp) }
        Text("$value$suffix", fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { if (value + step <= max) onChange(value + step) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun ToggleRow(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, name ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(name) })
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp))
    DfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private fun pinWidget(ctx: Context, receiver: Class<*>): Boolean {
    val awm = AppWidgetManager.getInstance(ctx)
    return if (awm.isRequestPinAppWidgetSupported) {
        awm.requestPinAppWidget(ComponentName(ctx, receiver), null, null)
    } else {
        false
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = Store.prefs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf("") }
    val notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        Group("Profile") {
            OutlinedTextField(
                value = p.name,
                onValueChange = { Store.updatePrefs(Store.prefs.copy(name = it)) },
                label = { Text("Your name (for the greeting)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Group("Appearance") {
            Text("Theme", fontSize = 12.sp, color = muted)
            Chips(listOf("Follow system", "Light", "Dark"), p.themeMode) { Store.updatePrefs(Store.prefs.copy(themeMode = it)) }
            Text("Accent colour", fontSize = 12.sp, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEachIndexed { i, c ->
                    Column(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(3.dp, if (p.accent == i) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                            .clickable { Store.updatePrefs(Store.prefs.copy(accent = i)) }
                    ) { }
                }
            }
            Text("Text size", fontSize = 12.sp, color = muted)
            Chips(listOf("Small", "Normal", "Large"), p.textScale) { Store.updatePrefs(Store.prefs.copy(textScale = it)) }
            ToggleRow("Haptic feedback", "A small buzz when you tick things off", p.haptics) { Store.updatePrefs(Store.prefs.copy(haptics = it)) }
        }

        Group("General") {
            Text("Open the app on", fontSize = 12.sp, color = muted)
            Chips(listOf("Home", "Calendar", "Tasks", "Habits", "Insights"), p.startTab) { Store.updatePrefs(Store.prefs.copy(startTab = it)) }
            Text("Time format", fontSize = 12.sp, color = muted)
            Chips(listOf("12-hour (AM/PM)", "24-hour"), if (p.use24h) 1 else 0) { Store.updatePrefs(Store.prefs.copy(use24h = it == 1)) }
            Text("Week starts on", fontSize = 12.sp, color = muted)
            Chips(listOf("Monday", "Sunday"), if (p.weekStartMon) 0 else 1) { Store.updatePrefs(Store.prefs.copy(weekStartMon = it == 0)) }
        }

        Group("Home screen sections") {
            val sections = listOf(
                "upcoming" to "Today's tasks", "habits" to "Habits", "review" to "Evening review card",
                "focus" to "Focus timer card", "suggestions" to "Suggestions"
            )
            for (s in sections) {
                ToggleRow(s.second, null, s.first !in p.hidden) { on ->
                    val h = Store.prefs.hidden
                    Store.updatePrefs(Store.prefs.copy(hidden = if (on) h - s.first else h + s.first))
                }
            }
        }

        Group("New task defaults") {
            Text("Priority", fontSize = 12.sp, color = muted)
            Chips(PriorityNames, p.defaultPriority) { Store.updatePrefs(Store.prefs.copy(defaultPriority = it)) }
            Stepper("Estimated minutes", p.defaultDuration, 0, 240, 5, " min") { Store.updatePrefs(Store.prefs.copy(defaultDuration = it)) }
            Text("Reminder, when switched on", fontSize = 12.sp, color = muted)
            Chips(listOf("At time", "10 min before", "30 min before", "1 hour before", "1 day before"), p.defaultRemindMode) {
                Store.updatePrefs(Store.prefs.copy(defaultRemindMode = it))
            }
        }

        Group("Categories") {
            for (c in p.categories) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(categoryEmoji(c) + " " + c, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Store.updatePrefs(Store.prefs.copy(categories = Store.prefs.categories - c)) }) {
                        Text("Remove", color = RedC, fontSize = 12.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it.take(20) },
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = newCategory.isNotBlank(),
                    onClick = {
                        Store.updatePrefs(Store.prefs.copy(categories = Store.prefs.categories + newCategory.trim()))
                        newCategory = ""
                    }
                ) { Text("Add") }
            }
        }

        Group("Planning and timers") {
            Stepper("Day starts at", p.dayStart, 0, 12, 1, ":00") { Store.updatePrefs(Store.prefs.copy(dayStart = it)) }
            Stepper("Day ends at", p.dayEnd, 13, 24, 1, ":00") { Store.updatePrefs(Store.prefs.copy(dayEnd = it)) }
            Stepper("Focus length", p.focusMin, 5, 120, 5, " min") { Store.updatePrefs(Store.prefs.copy(focusMin = it)) }
            Stepper("Short break", p.breakMin, 1, 30, 1, " min") { Store.updatePrefs(Store.prefs.copy(breakMin = it)) }
            Stepper("Long break", p.longBreakMin, 5, 60, 5, " min") { Store.updatePrefs(Store.prefs.copy(longBreakMin = it)) }
            Stepper("Snooze for", p.snoozeMin, 1, 60, 1, " min") { Store.updatePrefs(Store.prefs.copy(snoozeMin = it)) }
        }

        Group("Notifications") {
            Text(
                if (notifOn) "✅ Notifications are allowed for DayFlow." else "⚠️ Notifications are blocked. Reminders cannot appear until you allow them.",
                fontSize = 13.sp,
                color = if (notifOn) GreenC else RedC
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Reminders.sendTest(ctx)
                    toast("Test notification sent")
                }) { Text("Send test") }
                OutlinedButton(onClick = {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    ctx.startActivity(i)
                }) { Text("Phone settings") }
            }
            ToggleRow("Morning planning reminder", "A nudge to plan your day", p.morningOn) { Store.updatePrefs(Store.prefs.copy(morningOn = it)) }
            if (p.morningOn) {
                OutlinedButton(onClick = {
                    showTimePicker(ctx, p.morningTime) { Store.updatePrefs(Store.prefs.copy(morningTime = it)) }
                }) { Text("At " + fmtHHmm(p.morningTime)) }
            }
            ToggleRow("Evening review reminder", "A nudge to wrap up the day", p.eveningOn) { Store.updatePrefs(Store.prefs.copy(eveningOn = it)) }
            if (p.eveningOn) {
                OutlinedButton(onClick = {
                    showTimePicker(ctx, p.eveningTime) { Store.updatePrefs(Store.prefs.copy(eveningTime = it)) }
                }) { Text("At " + fmtHHmm(p.eveningTime)) }
            }
            Text(
                "Task and habit reminders are set on each task or habit. Some phones stop apps in the background; if reminders arrive late, set DayFlow's battery use to Unrestricted in phone settings.",
                fontSize = 11.sp,
                color = muted
            )
        }

        Group("Home screen widgets") {
            Text("Style", fontSize = 12.sp, color = muted)
            Chips(listOf("Dark", "Light", "Accent colour"), p.widgetTheme) { Store.updatePrefs(Store.prefs.copy(widgetTheme = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (pinWidget(ctx, DayFlowWidgetReceiver::class.java)) toast("Confirm adding the widget") else toast("Your launcher cannot add it from here. Long-press the home screen, tap Widgets, then choose DayFlow.")
                }) { Text("Add Today widget") }
                OutlinedButton(onClick = {
                    if (pinWidget(ctx, HabitsWidgetReceiver::class.java)) toast("Confirm adding the widget") else toast("Your launcher cannot add it from here. Long-press the home screen, tap Widgets, then choose DayFlow Habits.")
                }) { Text("Add Habits widget") }
            }
            Text(
                "Widgets can be resized: drag their edges after placing them to get a small, wide or large layout.",
                fontSize = 11.sp,
                color = muted
            )
        }

        Group("Your data") {
            Text(
                "Everything is stored only on this phone. DayFlow has no internet permission and sends nothing anywhere.",
                fontSize = 12.sp,
                color = muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("DayFlow backup", Store.exportJson()))
                    toast("Backup copied. Paste it into a notes app to keep it.")
                }) { Text("Copy backup") }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    if (text.isNullOrBlank()) toast("Clipboard is empty") else confirmRestore = text
                }) { Text("Restore backup") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Store.loadSample()
                    toast("Sample data added")
                }) { Text("Add sample data") }
                OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all data", color = RedC) }
            }
        }

        Group("About") {
            Text("DayFlow 1.1", fontWeight = FontWeight.Medium)
            Text("A local-first planner: tasks, habits, calendar, focus timer, reminders and home screen widgets.", fontSize = 12.sp, color = muted)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("All tasks, habits, reviews and focus history will be deleted from this phone. Settings are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearAll()
                    confirmClear = false
                    toast("All data cleared")
                }) { Text("Clear", color = RedC) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    val restoreText = confirmRestore
    if (restoreText != null) {
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore from clipboard?") },
            text = { Text("This replaces everything currently in DayFlow with the backup you copied.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = Store.importBackup(restoreText)
                    confirmRestore = null
                    toast(if (ok) "Backup restored" else "That is not a valid DayFlow backup")
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
}
