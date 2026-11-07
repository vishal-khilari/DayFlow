@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val Letters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun HabitsScreen() {
    val habits = Store.habits
    var dialog by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Habits", "Small steps, every day") {
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Add habit") }
        }
        if (habits.isEmpty()) {
            EmptyState("🌱", "No habits yet. Tap + to create one, for example drinking water or reading.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(habits.size) { i ->
                    val h = habits[i]
                    HabitCard(
                        h = h,
                        open = openId == h.id,
                        onToggleOpen = { openId = if (openId == h.id) null else h.id },
                        onEdit = { dialog = h },
                        onDelete = { deleting = h }
                    )
                }
            }
        }
    }

    if (creating || dialog != null) {
        HabitDialog(
            initial = dialog,
            onClose = {
                creating = false
                dialog = null
            }
        )
    }
    val del = deleting
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete habit?") },
            text = { Text("\"" + del.name + "\" and its history will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.deleteHabit(del.id)
                    deleting = null
                }) { Text("Delete", color = RedC) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HabitCard(h: Habit, open: Boolean, onToggleOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    var menu by remember { mutableStateOf(false) }
    val now = LocalDate.now()
    val week = h.rate(7)

    DfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.icon, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).clickable { onToggleOpen() }) {
                    Text(h.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "🔥 ${h.currentStreak()} day streak · " + (if (week == null) "no data yet" else "${Math.round(week * 100)}% this week"),
                        fontSize = 12.sp,
                        color = muted
                    )
                }
                HabitControl(h, now)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(if (now.toString() in h.skipped) "Un-skip today" else "Skip today") },
                            onClick = { menu = false; Store.toggleSkip(h.id, now.toString()) }
                        )
                        DropdownMenuItem(text = { Text("Delete", color = RedC) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            // week strip (tap a day to toggle yes/no habits)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (k in 6 downTo 0) {
                    val d = now.minusDays(k.toLong())
                    val ds = d.toString()
                    val sched = h.scheduledOn(d)
                    val isDone = h.doneOn(d)
                    val skipped = ds in h.skipped
                    val color = when {
                        isDone -> GreenC
                        skipped -> GreyC
                        !sched -> Color.Transparent
                        k == 0 -> MaterialTheme.colorScheme.surfaceVariant
                        else -> RedC.copy(alpha = 0.35f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Letters[d.dayOfWeek.value - 1], fontSize = 11.sp, color = muted)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable(enabled = sched && h.type == 0 && !skipped) {
                                    Store.setHabitEntry(h.id, ds, if (isDone) 0 else 1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isDone) "✓" else if (skipped) "–" else "", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                Text("Last 4 weeks", fontSize = 12.sp, color = muted)
                Spacer(Modifier.height(6.dp))
                val start = now.minusDays(27)
                for (row in 0..3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        for (col in 0..6) {
                            val d = start.plusDays((row * 7 + col).toLong())
                            val ds = d.toString()
                            val c = when {
                                h.doneOn(d) -> primary
                                ds in h.skipped -> GreyC.copy(alpha = 0.5f)
                                !h.scheduledOn(d) -> Color.Transparent
                                d == now -> MaterialTheme.colorScheme.surfaceVariant
                                else -> RedC.copy(alpha = 0.25f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(c)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val month = h.rate(30)
                Text("Longest streak: ${h.longestStreak()} days", fontSize = 13.sp)
                Text("Total completions: ${h.totalCompletions()}", fontSize = 13.sp)
                Text("30-day consistency: " + (if (month == null) "–" else "${Math.round(month * 100)}%"), fontSize = 13.sp)
                Text("Skipped days: ${h.skipped.size} (skipping never breaks a streak)", fontSize = 12.sp, color = muted)
            }
        }
    }
}

@Composable
private fun HabitDialog(initial: Habit?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "✅") }
    var type by remember { mutableIntStateOf(initial?.type ?: 0) }
    var target by remember { mutableStateOf((initial?.target ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var days by remember { mutableStateOf(initial?.days ?: listOf(1, 2, 3, 4, 5, 6, 7)) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val icons = listOf("✅", "🏃", "💧", "📚", "💻", "😴", "🥗", "🧘", "✍️", "🎸")
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial == null) "New habit" else "Edit habit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Icon", fontSize = 12.sp, color = muted)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (ic in icons) {
                        FilterChip(selected = icon == ic, onClick = { icon = ic }, label = { Text(ic) })
                    }
                }
                Text("Type", fontSize = 12.sp, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == 0, onClick = { type = 0 }, label = { Text("Yes / No") })
                    FilterChip(selected = type == 1, onClick = { type = 1 }, label = { Text("Count / amount") })
                }
                if (type == 1) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { v -> target = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Daily target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (glasses, pages, minutes…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Repeat on", fontSize = 12.sp, color = muted)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..7) {
                        FilterChip(
                            selected = i in days,
                            onClick = { days = if (i in days) days - i else (days + i).sorted() },
                            label = { Text(dayNames[i - 1]) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && days.isNotEmpty(),
                onClick = {
                    val tg = if (type == 0) 1 else maxOf(1, target.toIntOrNull() ?: 1)
                    if (initial == null) {
                        Store.addHabit(Habit(id = Store.newId(), name = name.trim(), icon = icon, type = type, target = tg, unit = unit.trim(), days = days))
                    } else {
                        Store.updateHabit(initial.copy(name = name.trim(), icon = icon, type = type, target = tg, unit = unit.trim(), days = days))
                    }
                    onClose()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
