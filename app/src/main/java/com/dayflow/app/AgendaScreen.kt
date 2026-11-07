@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun AgendaScreen(actions: TaskActions) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val tasks = Store.tasks
    val ds = selected.toString()
    val isToday = selected == LocalDate.now()

    val dayTasks = tasks.filter { it.due == ds && it.status != 2 }
    val overdue = if (isToday) tasks.filter { it.isOverdue() } else emptyList()
    val timed = dayTasks.filter { it.time != null }.sortedBy { it.time }
    val untimed = dayTasks.filter { it.time == null }.sortedByDescending { it.priority }
    val overlapIds = timed.filter { a -> timed.any { b -> b.id != a.id && overlaps(a, b) } }.map { it.id }.toSet()
    val habitsForDay = Store.habits.filter { it.scheduledOn(selected) }

    val planned = dayTasks.filter { it.status == 0 }.sumOf { it.durationMin }
    val now = LocalTime.now()
    val window = if (isToday) max(0, Store.prefs.dayEnd * 60 - (now.hour * 60 + now.minute)) else max(60, (Store.prefs.dayEnd - 7) * 60)
    val free = window - planned

    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader("Calendar", selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))) {
                TextButton(onClick = {
                    DatePickerDialog(ctx, { _, y, m, d ->
                        selected = LocalDate.of(y, m + 1, d)
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                }) { Text("Pick date") }
                TextButton(onClick = { selected = LocalDate.now() }) { Text("Today") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selected = selected.minusDays(7) }) { Text("‹") }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0..6) {
                        val d = weekStart.plusDays(i.toLong())
                        val isSel = d == selected
                        val count = tasks.count { it.due == d.toString() && it.status != 2 }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selected = d }.padding(horizontal = 2.dp)
                        ) {
                            Text(DayLetters[i], fontSize = 11.sp, color = muted)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    d.dayOfMonth.toString(),
                                    color = if (isSel) Color.White else Color.Unspecified,
                                    fontWeight = if (d == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (count > 0) primary else Color.Transparent)
                            )
                        }
                    }
                }
                TextButton(onClick = { selected = selected.plusDays(7) }) { Text("›") }
            }
        }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Planned", fontSize = 12.sp, color = muted)
                        Text(fmtMin(planned), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(if (isToday) "Free until ${Store.prefs.dayEnd}:00" else "Free in a normal day", fontSize = 12.sp, color = muted)
                        Text(
                            if (free >= 0) fmtMin(free) else "Over by " + fmtMin(-free),
                            fontWeight = FontWeight.Bold,
                            color = if (free >= 0) GreenC else RedC
                        )
                    }
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item { SectionLabel("Overdue") }
            items(overdue.size) { i -> TaskLine(overdue[i], actions) }
        }
        if (timed.isNotEmpty()) {
            item { SectionLabel("Schedule") }
            items(timed.size) { i ->
                val t = timed[i]
                val start = startMinutes(t) ?: 0
                val endText = if (t.durationMin > 0) " – " + fmtClock(start + t.durationMin) else ""
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.width(78.dp).padding(top = 14.dp)) {
                        Text(fmtClock(start) + endText, fontSize = 11.sp, color = muted)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TaskLine(t, actions, if (t.id in overlapIds) "⚠ Overlaps with another task" else null)
                    }
                }
            }
        }
        if (untimed.isNotEmpty()) {
            item { SectionLabel("Anytime today") }
            items(untimed.size) { i -> TaskLine(untimed[i], actions) }
        }
        if (habitsForDay.isNotEmpty()) {
            item { SectionLabel("Habits") }
            items(habitsForDay.size) { i ->
                val h = habitsForDay[i]
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(h.icon, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(h.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (!selected.isAfter(LocalDate.now())) HabitControl(h, selected)
                    }
                }
            }
        }
        if (dayTasks.isEmpty() && overdue.isEmpty() && habitsForDay.isEmpty()) {
            item { EmptyState("🗓️", "Nothing planned for this day. Tap + to add a task.") }
        }
    }
}
