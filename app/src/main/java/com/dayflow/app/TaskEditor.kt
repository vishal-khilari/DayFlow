@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RemindModes = listOf("At due time", "10 min before", "30 min before", "1 hour before", "1 day before", "Custom date & time")
private val RemindOffsets = listOf(0L, 10L, 30L, 60L, 1440L)

@Composable
fun TaskEditor(req: EditorRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = Store.prefs
    val t0 = req.task
    var title by remember { mutableStateOf(t0?.title ?: "") }
    var notes by remember { mutableStateOf(t0?.notes ?: "") }
    var priority by remember { mutableIntStateOf(t0?.priority ?: prefs.defaultPriority) }
    var due by remember { mutableStateOf(t0?.due) }
    var time by remember { mutableStateOf(t0?.time) }
    var dur by remember {
        mutableStateOf(
            if (t0 != null) {
                if (t0.durationMin == 0) "" else t0.durationMin.toString()
            } else {
                if (prefs.defaultDuration == 0) "" else prefs.defaultDuration.toString()
            }
        )
    }
    var category by remember { mutableStateOf(t0?.category ?: "") }
    var remind by remember { mutableStateOf(t0?.remind ?: false) }
    var remindMode by remember {
        mutableIntStateOf(if (t0 != null && t0.remind && t0.remindAt > 0L) 5 else prefs.defaultRemindMode.coerceIn(0, 4))
    }
    val initial: LocalDateTime? = if (t0 != null && t0.remindAt > 0L) {
        Instant.ofEpochMilli(t0.remindAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } else null
    var rDate by remember { mutableStateOf(initial?.toLocalDate()?.toString()) }
    var rTime by remember { mutableStateOf(initial?.let { "%02d:%02d".format(it.hour, it.minute) }) }

    val parentTitle = req.parentId?.let { pid -> Store.tasks.firstOrNull { it.id == pid }?.title }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val td = todayStr()
    val tomorrow = LocalDate.now().plusDays(1).toString()
    val nextWeek = LocalDate.now().plusDays(7).toString()

    /** When the notification will fire, as epoch millis, or 0 when it cannot be worked out yet. */
    fun computeRemindAt(): Long {
        val zone = ZoneId.systemDefault()
        if (remindMode == 5) {
            val d = parseDate(rDate) ?: return 0L
            return LocalDateTime.of(d, parseTime(rTime ?: "09:00")).atZone(zone).toInstant().toEpochMilli()
        }
        val effectiveDue = if (due == null && time != null) td else due
        val dd = parseDate(effectiveDue) ?: return 0L
        val base = LocalDateTime.of(dd, parseTime(time ?: "09:00"))
        return base.minusMinutes(RemindOffsets[remindMode]).atZone(zone).toInstant().toEpochMilli()
    }

    val remindMillis = if (remind) computeRemindAt() else 0L
    val summary: String = if (!remind) {
        ""
    } else if (remindMillis <= 0L) {
        "Choose a date for the task above, or pick Custom date & time to set the reminder yourself."
    } else {
        val dt = Instant.ofEpochMilli(remindMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val text = dt.format(DateTimeFormatter.ofPattern("EEE d MMM")) + " at " + fmtClock(dt.hour * 60 + dt.minute)
        if (remindMillis < System.currentTimeMillis()) "That time has already passed ($text)." else "You will be notified on $text."
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(if (t0 != null) "Edit task" else if (parentTitle != null) "New subtask" else "New task")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (parentTitle != null) {
                    Text("Inside: $parentTitle", fontSize = 12.sp, color = muted)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0..3) {
                        FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(PriorityNames[i]) })
                    }
                }

                Text("Date", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = due == null, onClick = { due = null }, label = { Text("No date (Inbox)") })
                    FilterChip(selected = due == td, onClick = { due = td }, label = { Text("Today") })
                    FilterChip(selected = due == tomorrow, onClick = { due = tomorrow }, label = { Text("Tomorrow") })
                    FilterChip(selected = due == nextWeek, onClick = { due = nextWeek }, label = { Text("Next week") })
                    val custom = due != null && due != td && due != tomorrow && due != nextWeek
                    FilterChip(
                        selected = custom,
                        onClick = { showDatePicker(ctx, parseDate(due)) { due = it.toString() } },
                        label = { Text(if (custom) dueLabel(due ?: "") else "Pick date…") }
                    )
                }
                if (due == null) {
                    Text("Tasks without a date go to your Inbox. Capture the idea now and schedule it later.", fontSize = 11.sp, color = muted)
                }

                Text("Time", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { showTimePicker(ctx, time) { time = it } }) {
                        Text(if (time != null) fmtHHmm(time ?: "") else "Set time")
                    }
                    for (q in listOf("09:00", "12:00", "15:00", "18:00", "21:00")) {
                        FilterChip(selected = time == q, onClick = { time = q }, label = { Text(fmtHHmm(q)) })
                    }
                    if (time != null) TextButton(onClick = { time = null }) { Text("Clear") }
                }

                OutlinedTextField(
                    value = dur,
                    onValueChange = { v -> dur = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Estimated minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (m in listOf(15, 30, 45, 60, 90, 120)) {
                        FilterChip(selected = dur == m.toString(), onClick = { dur = m.toString() }, label = { Text(fmtMin(m)) })
                    }
                }

                Text("Category", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in prefs.categories) {
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) "" else c },
                            label = { Text(categoryEmoji(c) + " " + c) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("🔔 Remind me", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
                if (remind) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RemindModes.forEachIndexed { i, name ->
                            FilterChip(selected = remindMode == i, onClick = { remindMode = i }, label = { Text(name) })
                        }
                    }
                    if (remindMode == 5) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                showDatePicker(ctx, parseDate(rDate)) { rDate = it.toString() }
                            }) { Text(if (rDate != null) dueLabel(rDate ?: "") else "Reminder date") }
                            OutlinedButton(onClick = {
                                showTimePicker(ctx, rTime) { rTime = it }
                            }) { Text(if (rTime != null) fmtHHmm(rTime ?: "") else "Reminder time") }
                        }
                    }
                    Text(summary, fontSize = 12.sp, color = if (remindMillis > System.currentTimeMillis()) GreenC else muted)
                    if (remindMode != 5 && time == null) {
                        Text("No time is set on the task, so the reminder uses 9:00 AM on that day.", fontSize = 11.sp, color = muted)
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val d = dur.toIntOrNull() ?: 0
                    val finalDue = if (due == null && time != null) td else due
                    val ra = if (remind) computeRemindAt() else 0L
                    val finalRemind = remind && ra > 0L
                    if (t0 == null) {
                        Store.addTask(
                            Task(
                                id = Store.newId(), parentId = req.parentId, title = title.trim(),
                                notes = notes.trim(), priority = priority, due = finalDue, time = time,
                                durationMin = d, remind = finalRemind, remindAt = if (finalRemind) ra else 0L,
                                category = category, order = Store.nextOrder()
                            )
                        )
                    } else {
                        Store.updateTask(
                            t0.copy(
                                title = title.trim(), notes = notes.trim(), priority = priority,
                                due = finalDue, time = time, durationMin = d,
                                remind = finalRemind, remindAt = if (finalRemind) ra else 0L, category = category
                            )
                        )
                    }
                    onClose()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
