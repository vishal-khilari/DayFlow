@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskEditor(req: EditorRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t0 = req.task
    var title by remember { mutableStateOf(t0?.title ?: "") }
    var notes by remember { mutableStateOf(t0?.notes ?: "") }
    var priority by remember { mutableIntStateOf(t0?.priority ?: 1) }
    var due by remember { mutableStateOf(t0?.due) }
    var time by remember { mutableStateOf(t0?.time) }
    var dur by remember {
        mutableStateOf(if ((t0?.durationMin ?: 0) == 0) "" else (t0?.durationMin ?: 0).toString())
    }
    var remind by remember { mutableStateOf(t0?.remind ?: false) }
    var category by remember { mutableStateOf(t0?.category ?: "") }
    val parentTitle = req.parentId?.let { pid -> Store.tasks.firstOrNull { it.id == pid }?.title }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    fun pickDate() {
        val d = parseDate(due) ?: LocalDate.now()
        DatePickerDialog(ctx, { _, y, m, day ->
            due = LocalDate.of(y, m + 1, day).toString()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun pickTime() {
        val parts = (time ?: "09:00").split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(ctx, { _, hh, mm ->
            time = "%02d:%02d".format(hh, mm)
        }, h, m, true).show()
    }

    val td = todayStr()
    val tomorrow = LocalDate.now().plusDays(1).toString()
    val categories = listOf("Study", "Work", "Health", "Personal", "Other")

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                if (t0 != null) "Edit task" else if (parentTitle != null) "New subtask" else "New task"
            )
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
                        FilterChip(
                            selected = priority == i,
                            onClick = { priority = i },
                            label = { Text(PriorityNames[i]) }
                        )
                    }
                }
                Text("Date", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = due == null, onClick = { due = null }, label = { Text("Inbox") })
                    FilterChip(selected = due == td, onClick = { due = td }, label = { Text("Today") })
                    FilterChip(selected = due == tomorrow, onClick = { due = tomorrow }, label = { Text("Tomorrow") })
                    FilterChip(
                        selected = due != null && due != td && due != tomorrow,
                        onClick = { pickDate() },
                        label = { Text(if (due != null && due != td && due != tomorrow) dueLabel(due ?: "") else "Pick date") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime() }) { Text(time ?: "Set time") }
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
                    for (m in listOf(15, 30, 60, 90, 120)) {
                        FilterChip(
                            selected = dur == m.toString(),
                            onClick = { dur = m.toString() },
                            label = { Text(fmtMin(m)) }
                        )
                    }
                }
                Text("Category", fontSize = 12.sp, color = muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in categories) {
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) "" else c },
                            label = { Text(c) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me (needs a date)", modifier = Modifier.weight(1f))
                    Switch(checked = remind, onCheckedChange = { remind = it })
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
                    if (t0 == null) {
                        Store.addTask(
                            Task(
                                id = Store.newId(), parentId = req.parentId, title = title.trim(),
                                notes = notes.trim(), priority = priority, due = finalDue, time = time,
                                durationMin = d, remind = remind && finalDue != null,
                                category = category, order = Store.nextOrder()
                            )
                        )
                    } else {
                        Store.updateTask(
                            t0.copy(
                                title = title.trim(), notes = notes.trim(), priority = priority,
                                due = finalDue, time = time, durationMin = d,
                                remind = remind && finalDue != null, category = category
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
