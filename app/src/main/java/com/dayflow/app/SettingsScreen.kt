@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, suffix: String, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−", fontSize = 20.sp) }
        Text("$value$suffix", fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = Store.prefs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }

    fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        SectionTitle("Profile")
        OutlinedTextField(
            value = p.name,
            onValueChange = { Store.updatePrefs(Store.prefs.copy(name = it)) },
            label = { Text("Your name (for the greeting)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Follow system", "Light", "Dark").forEachIndexed { i, name ->
                FilterChip(
                    selected = p.themeMode == i,
                    onClick = { Store.updatePrefs(Store.prefs.copy(themeMode = i)) },
                    label = { Text(name) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Accent colour", fontSize = 13.sp, color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
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

        SectionTitle("Home screen sections")
        val sections = listOf("upcoming" to "Today's tasks", "habits" to "Habits", "focus" to "Focus timer card", "suggestions" to "Suggestions")
        for (s in sections) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(s.second, modifier = Modifier.weight(1f))
                Switch(
                    checked = s.first !in p.hidden,
                    onCheckedChange = { on ->
                        val h = Store.prefs.hidden
                        Store.updatePrefs(Store.prefs.copy(hidden = if (on) h - s.first else h + s.first))
                    }
                )
            }
        }

        SectionTitle("Planning and timers")
        Stepper("Day ends at", p.dayEnd, 12, 24, ":00") { Store.updatePrefs(Store.prefs.copy(dayEnd = it)) }
        Stepper("Focus length", p.focusMin, 5, 120, " min") { Store.updatePrefs(Store.prefs.copy(focusMin = it)) }
        Stepper("Short break", p.breakMin, 1, 30, " min") { Store.updatePrefs(Store.prefs.copy(breakMin = it)) }
        Stepper("Long break", p.longBreakMin, 5, 60, " min") { Store.updatePrefs(Store.prefs.copy(longBreakMin = it)) }
        Stepper("Snooze reminders for", p.snoozeMin, 1, 60, " min") { Store.updatePrefs(Store.prefs.copy(snoozeMin = it)) }

        SectionTitle("Your data")
        Text(
            "Everything is stored only on this phone. DayFlow has no internet permission and sends nothing anywhere.",
            fontSize = 12.sp,
            color = muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
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

        SectionTitle("About")
        Text("DayFlow 1.0", fontWeight = FontWeight.Medium)
        Text("A local-first planner: tasks, habits, calendar, focus timer and a home screen widget.", fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("All tasks, habits and focus history will be deleted from this phone. Settings are kept.") },
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
