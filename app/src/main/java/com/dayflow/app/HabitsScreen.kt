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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val Letters = listOf("M", "T", "W", "T", "F", "S", "S")

private val EmojiGroups = listOf(
    "Body" to listOf("🏃", "🏋️", "🚴", "🏊", "🧘", "💧", "🥗", "🍎", "😴", "🚶", "🦷", "🧴"),
    "Mind" to listOf("🧠", "📚", "📖", "✍️", "🎓", "💡", "🔬", "🧩", "♟️", "🗣️", "🔢", "🌍"),
    "Creative" to listOf("🎨", "🖌️", "🎸", "🎹", "🎤", "🎬", "📷", "🧶", "✂️", "🎭", "🎻", "🥁"),
    "Life" to listOf("💻", "🧹", "🛏️", "💰", "📞", "⏰", "🎯", "🌱", "🌞", "🙏", "❤️", "🐶")
)

private class QuickHabit(val name: String, val icon: String, val type: Int, val target: Int, val unit: String)

private val QuickHabits = listOf(
    QuickHabit("Drink water", "💧", 1, 8, "glasses"),
    QuickHabit("Meditate", "🧘", 1, 10, "minutes"),
    QuickHabit("Read", "📖", 1, 10, "pages"),
    QuickHabit("Journal", "✍️", 0, 1, ""),
    QuickHabit("Sketch or paint", "🎨", 1, 20, "minutes"),
    QuickHabit("Practice music", "🎸", 1, 30, "minutes"),
    QuickHabit("Think deeply", "🧠", 1, 15, "minutes"),
    QuickHabit("Workout", "🏋️", 0, 1, ""),
    QuickHabit("Sleep on time", "😴", 0, 1, "")
)

@Composable
fun HabitsScreen() {
    val habits = Store.habits
    var dialog by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    val done = habits.count { it.scheduledOn(LocalDate.now()) && it.doneOn(LocalDate.now()) }
    val sched = habits.count { it.scheduledOn(LocalDate.now()) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Habits", if (sched == 0) "Small steps, every day" else "$done of $sched done today") {
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Add habit") }
        }
        if (habits.isEmpty()) {
            EmptyState("🌱", "No habits yet. Tap + to create one, for example drinking water, meditating or sketching.")
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
    val rt = h.remindTime
    val letters = Letters

    DfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text(h.icon, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).clickable { onToggleOpen() }) {
                    Text(h.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "🔥 ${h.currentStreak()} · " + (if (week == null) "no data yet" else "${Math.round(week * 100)}% this week"),
                        fontSize = 12.sp,
                        color = muted
                    )
                    if (rt != null) Text("🔔 " + fmtHHmm(rt), fontSize = 11.sp, color = muted)
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                        Text(letters[d.dayOfWeek.value - 1], fontSize = 11.sp, color = muted)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
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
                Spacer(Modifier.height(14.dp))
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
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(c))
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
private fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (group in EmojiGroups) {
            Text(group.first, fontSize = 11.sp, color = muted, modifier = Modifier.padding(top = 4.dp))
            for (rowItems in group.second.chunked(6)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (e in rowItems) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (e == selected) primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSelect(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitDialog(initial: Habit?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "✅") }
    var type by remember { mutableIntStateOf(initial?.type ?: 0) }
    var target by remember { mutableStateOf((initial?.target ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var days by remember { mutableStateOf(initial?.days ?: listOf(1, 2, 3, 4, 5, 6, 7)) }
    var remindTime by remember { mutableStateOf(initial?.remindTime) }
    var custom by remember { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial == null) "New habit" else "Edit habit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initial == null) {
                    Text("Quick ideas", fontSize = 12.sp, color = muted)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (q in QuickHabits) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    name = q.name
                                    icon = q.icon
                                    type = q.type
                                    target = q.target.toString()
                                    unit = q.unit
                                },
                                label = { Text(q.icon + " " + q.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Icon:", fontSize = 12.sp, color = muted)
                    Spacer(Modifier.width(8.dp))
                    Text(icon, fontSize = 28.sp)
                }
                EmojiPicker(selected = icon, onSelect = { icon = it })
                OutlinedTextField(
                    value = custom,
                    onValueChange = {
                        custom = it.take(4)
                        if (custom.isNotBlank()) icon = custom.trim()
                    },
                    label = { Text("Or type any emoji from your keyboard") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                Text("Daily reminder", fontSize = 12.sp, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showTimePicker(ctx, remindTime ?: "08:00") { remindTime = it } }) {
                        Text(if (remindTime != null) "🔔 " + fmtHHmm(remindTime ?: "") else "Set reminder time")
                    }
                    if (remindTime != null) TextButton(onClick = { remindTime = null }) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && days.isNotEmpty(),
                onClick = {
                    val tg = if (type == 0) 1 else maxOf(1, target.toIntOrNull() ?: 1)
                    if (initial == null) {
                        Store.addHabit(
                            Habit(
                                id = Store.newId(), name = name.trim(), icon = icon, type = type, target = tg,
                                unit = unit.trim(), days = days, remindTime = remindTime
                            )
                        )
                    } else {
                        Store.updateHabit(
                            initial.copy(name = name.trim(), icon = icon, type = type, target = tg, unit = unit.trim(), days = days, remindTime = remindTime)
                        )
                    }
                    onClose()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
