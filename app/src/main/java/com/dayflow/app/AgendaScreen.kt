@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val MonLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val SunLetters = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun AgendaScreen(actions: TaskActions) {
    val ctx = LocalContext.current
    val prefs = Store.prefs
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableIntStateOf(1) } // 0 = week strip, 1 = full month
    var showPlan by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val tasks = Store.tasks
    val ds = selected.toString()
    val isToday = selected == LocalDate.now()
    val letters = if (prefs.weekStartMon) MonLetters else SunLetters
    val weekStartValue = if (prefs.weekStartMon) 1 else 7

    val dayTasks = tasks.filter { it.due == ds && it.status != 2 }
    val overdue = if (isToday) tasks.filter { it.isOverdue() } else emptyList()
    val timed = dayTasks.filter { it.time != null }.sortedBy { it.time }
    val untimed = dayTasks.filter { it.time == null }.sortedByDescending { it.priority }
    val overlapIds = timed.filter { a -> timed.any { b -> b.id != a.id && overlaps(a, b) } }.map { it.id }.toSet()
    val habitsForDay = Store.habits.filter { it.scheduledOn(selected) }
    val byDate = tasks.filter { it.status != 2 && it.due != null }.groupBy { it.due ?: "" }

    val planned = dayTasks.filter { it.status == 0 }.sumOf { it.durationMin }
    val now = LocalTime.now()
    val window = if (isToday) max(0, prefs.dayEnd * 60 - (now.hour * 60 + now.minute)) else max(60, (prefs.dayEnd - prefs.dayStart) * 60)
    val free = window - planned

    val ym = YearMonth.from(selected)
    val first = ym.atDay(1)
    val lead = (first.dayOfWeek.value - weekStartValue + 7) % 7
    val rows = (lead + ym.lengthOfMonth() + 6) / 7
    val weekStart = selected.minusDays(((selected.dayOfWeek.value - weekStartValue + 7) % 7).toLong())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader("Calendar", selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))) {
                TextButton(onClick = { selected = LocalDate.now() }) { Text("Today") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Month") })
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Week") })
                FilterChip(selected = false, onClick = { showDatePicker(ctx, selected) { selected = it } }, label = { Text("Jump to date") })
            }
        }
        if (mode == 1) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selected = selected.minusMonths(1) }) { Text("‹", fontSize = 20.sp) }
                            Text(
                                ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            TextButton(onClick = { selected = selected.plusMonths(1) }) { Text("›", fontSize = 20.sp) }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (l in letters) {
                                Text(
                                    l,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = muted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Spacer(Modifier.size(4.dp))
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (c in 0..6) {
                                    val dayNum = r * 7 + c - lead + 1
                                    if (dayNum < 1 || dayNum > ym.lengthOfMonth()) {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val d = ym.atDay(dayNum)
                                        val list = byDate[d.toString()] ?: emptyList()
                                        val isSel = d == selected
                                        val isNow = d == LocalDate.now()
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) primary else Color.Transparent)
                                                .then(
                                                    if (isNow && !isSel) Modifier.border(1.5.dp, primary, RoundedCornerShape(12.dp)) else Modifier
                                                )
                                                .clickable { selected = d },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                dayNum.toString(),
                                                fontSize = 14.sp,
                                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) Color.White else Color.Unspecified
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) {
                                                val dots = list.sortedByDescending { it.priority }.take(3)
                                                for (t in dots) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSel) Color.White else priorityColor(t.priority))
                                                    )
                                                }
                                                if (dots.isEmpty()) Box(modifier = Modifier.size(5.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = selected.minusDays(7) }) { Text("‹") }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0..6) {
                            val d = weekStart.plusDays(i.toLong())
                            val isSel = d == selected
                            val count = (byDate[d.toString()] ?: emptyList()).size
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { selected = d }.padding(horizontal = 2.dp)
                            ) {
                                Text(letters[i], fontSize = 11.sp, color = muted)
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isSel) primary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.dayOfMonth.toString(),
                                        color = if (isSel) Color.White else Color.Unspecified,
                                        fontWeight = if (d == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Box(
                                    modifier = Modifier.size(5.dp).clip(CircleShape).background(if (count > 0) primary else Color.Transparent)
                                )
                            }
                        }
                    }
                    TextButton(onClick = { selected = selected.plusDays(7) }) { Text("›") }
                }
            }
        }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Planned", fontSize = 12.sp, color = muted)
                        Text(fmtMin(planned), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(if (isToday) "Free until " + fmtClock(prefs.dayEnd * 60) else "Free in a normal day", fontSize = 12.sp, color = muted)
                        Text(
                            if (free >= 0) fmtMin(free) else "Over by " + fmtMin(-free),
                            fontWeight = FontWeight.Bold,
                            color = if (free >= 0) GreenC else RedC
                        )
                    }
                    TextButton(onClick = { showPlan = true }) { Text("✨ Plan my day") }
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
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.width(74.dp).padding(top = 14.dp)) {
                        Text(fmtClock(start), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        if (t.durationMin > 0) Text(fmtClock(start + t.durationMin), fontSize = 11.sp, color = muted)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TaskLine(t, actions, if (t.id in overlapIds) "⚠ Overlaps with another task" else null)
                    }
                }
            }
        }
        if (untimed.isNotEmpty()) {
            item { SectionLabel("Anytime") }
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
                        Text(h.icon, fontSize = 22.sp)
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

    if (showPlan) {
        val plan = Store.planDay(ds)
        AlertDialog(
            onDismissRequest = { showPlan = false },
            title = { Text("Plan my day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (plan.first.isEmpty() && plan.second.isEmpty()) {
                        Text("Every task on this day already has a time, or there are none. Add tasks with a date but no time, and I will find room for them.")
                    }
                    for (p in plan.first) {
                        Text(fmtClock(p.second) + "   " + p.first.title)
                    }
                    if (plan.second.isNotEmpty()) {
                        Text("These do not fit before your day ends:", color = RedC, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        for (t in plan.second) Text("• " + t.title, fontSize = 13.sp)
                        Text("Move them to another day or shorten something.", fontSize = 12.sp, color = muted)
                    }
                    if (plan.first.isNotEmpty()) {
                        Text("Priorities go first, existing times are never moved.", fontSize = 12.sp, color = muted)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = plan.first.isNotEmpty(), onClick = {
                    Store.applyPlan(plan.first)
                    showPlan = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPlan = false }) { Text("Close") } }
        )
    }
}
