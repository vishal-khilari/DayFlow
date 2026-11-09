@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun HomeScreen(
    actions: TaskActions,
    onFocus: () -> Unit,
    onSettings: () -> Unit,
    onReview: () -> Unit,
    onTab: (Int) -> Unit
) {
    val tasks = Store.tasks
    val habits = Store.habits
    val prefs = Store.prefs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val td = todayStr()
    val now = LocalTime.now()
    val nowStr = "%02d:%02d".format(now.hour, now.minute)

    val eligible = Store.eligibleToday()
    val total = eligible.size
    val done = eligible.count { it.status == 1 }
    val pending = eligible.count { it.status == 0 }
    val overdue = tasks.count { it.isOverdue() }

    val todayOpen = tasks.filter { it.status == 0 && it.due == td }
    val upcoming = todayOpen.sortedWith(compareBy<Task>({ it.time ?: "99:99" }, { -it.priority }))
    val next = upcoming.firstOrNull { val tm = it.time; tm != null && tm >= nowStr } ?: upcoming.firstOrNull()

    val leftMin = max(0, prefs.dayEnd * 60 - (now.hour * 60 + now.minute))
    val free = max(0, leftMin - todayOpen.sumOf { it.durationMin })
    val focusToday = Store.focusMinutes(td)
    val score = Store.weeklyScore()
    val todaysHabits = habits.filter { it.scheduledOn(LocalDate.now()) && td !in it.skipped }
    val habitsDone = todaysHabits.count { it.doneOn(LocalDate.now()) }
    val recs = Store.recommendations()
    val reviewedToday = Store.reviews.any { it.date == td }

    val greeting = when {
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    } + (if (prefs.name.isNotBlank()) ", " + prefs.name.trim() else "")
    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(greeting, dateText) {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.35f))))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            progress = if (total == 0) 0f else done.toFloat() / total,
                            dia = 104.dp,
                            label = if (total == 0) "–" else (done * 100 / total).toString() + "%",
                            fillColor = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            textColor = Color.White
                        )
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text("Daily progress", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(
                                if (total == 0) "Nothing planned" else "$done/$total tasks completed",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HeroChip("$pending pending")
                                if (overdue > 0) HeroChip("$overdue overdue")
                            }
                        }
                    }
                    Text(
                        "Counts tasks due today plus overdue ones. Skipped tasks are left out and future tasks never count.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Focus today", fmtMin(focusToday), Modifier.weight(1f))
                StatCard("Free time", fmtMin(free), Modifier.weight(1f))
                StatCard(
                    "Habits",
                    if (todaysHabits.isEmpty()) "–" else "$habitsDone/${todaysHabits.size}",
                    Modifier.weight(1f)
                )
                StatCard("Week score", if (score == null) "–" else score.toString(), Modifier.weight(1f))
            }
        }
        if (overdue > 0) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠️ $overdue overdue task" + (if (overdue == 1) "" else "s"),
                            color = RedC,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { Store.rolloverOverdue() }) { Text("Move to today") }
                    }
                }
            }
        }
        if (next != null) {
            item { SectionLabel("Next up") }
            item { TaskLine(next, actions) }
        }
        if ("upcoming" !in prefs.hidden) {
            item { SectionLabel("Today's tasks") }
            if (upcoming.isEmpty()) {
                item { EmptyState("🎉", "No open tasks for today.") }
            } else {
                items(upcoming.take(8).size) { i -> TaskLine(upcoming[i], actions) }
            }
        }
        if ("habits" !in prefs.hidden) {
            item { SectionLabel("Habits today") }
            if (todaysHabits.isEmpty()) {
                item { TextButton(onClick = { onTab(3) }) { Text("Add your first habit") } }
            } else {
                item {
                    LinearProgressIndicator(
                        progress = { habitsDone.toFloat() / todaysHabits.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
                items(todaysHabits.size) { i ->
                    val h = todaysHabits[i]
                    DfCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) { Text(h.icon, fontSize = 20.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(h.name, fontWeight = FontWeight.Medium)
                                Text("🔥 ${h.currentStreak()} day streak", fontSize = 12.sp, color = muted)
                            }
                            HabitControl(h, LocalDate.now())
                        }
                    }
                }
            }
        }
        if ("review" !in prefs.hidden) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth().clickable { onReview() }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reviewedToday) "✅" else "🌙", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evening review", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (reviewedToday) "Done for today. Tap to redo it." else "Wrap up the day and set up tomorrow.",
                                fontSize = 12.sp,
                                color = muted
                            )
                        }
                    }
                }
            }
        }
        if ("focus" !in prefs.hidden) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱️", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Focus timer", fontWeight = FontWeight.SemiBold)
                            Text("${fmtMin(focusToday)} focused today", fontSize = 12.sp, color = muted)
                        }
                        Button(onClick = onFocus) { Text("Start") }
                    }
                }
            }
        }
        if ("suggestions" !in prefs.hidden && recs.isNotEmpty()) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth().clickable { onTab(4) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💡 " + recs.first().title, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (recs.size > 1) "${recs.size - 1} more suggestion(s) in Insights" else "Open Insights for details",
                            fontSize = 12.sp,
                            color = muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
