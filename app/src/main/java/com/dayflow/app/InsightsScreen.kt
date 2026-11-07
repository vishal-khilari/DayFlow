@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun InsightsScreen() {
    val tasks = Store.tasks
    val habits = Store.habits
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val now = LocalDate.now()
    val last7 = (6 downTo 0).map { now.minusDays(it.toLong()) }
    val labels = last7.map { Days[it.dayOfWeek.value - 1] }
    val completed = last7.map { d -> tasks.count { it.status == 1 && it.doneOn == d.toString() } }
    val focus = last7.map { d -> Store.focusMinutes(d.toString()) }
    val score = Store.weeklyScore()
    val recs = Store.recommendations()
    val ratio = Store.timeRealityRatio()

    val cutoff = now.minusDays(29).toString()
    val byCat = tasks.filter { it.status == 1 && (it.doneOn ?: "") >= cutoff }
        .groupBy { if (it.category.isBlank()) "Uncategorised" else it.category }
        .map { Pair(it.key, it.value.size) }
        .sortedByDescending { it.second }
    val maxCat = maxOf(1, byCat.maxOfOrNull { it.second } ?: 1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Insights", "How your week is going") }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Weekly score", fontSize = 13.sp, color = muted)
                    Text(if (score == null) "–" else "$score / 100", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "50% tasks finished out of those due in the last 7 days, 30% habit consistency, 20% focus time (goal: 5 hours a week). " +
                            "Parts you don't use are left out, and adding more tasks does not raise the score. " +
                            "It is a rough guide, not a measure of you.",
                        fontSize = 11.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tasks completed, last 7 days", fontWeight = FontWeight.SemiBold)
                    Text("Total: ${completed.sum()}", fontSize = 12.sp, color = muted, modifier = Modifier.padding(bottom = 8.dp))
                    BarChart(completed, labels, MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Focus minutes, last 7 days", fontWeight = FontWeight.SemiBold)
                    Text("Total: ${fmtMin(focus.sum())}", fontSize = 12.sp, color = muted, modifier = Modifier.padding(bottom = 8.dp))
                    BarChart(focus, labels, BlueC)
                }
            }
        }
        if (habits.isNotEmpty()) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Habit consistency (7 days)", fontWeight = FontWeight.SemiBold)
                        for (h in habits) {
                            val r = h.rate(7)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(h.icon + " " + h.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    Text(if (r == null) "–" else "${Math.round(r * 100)}%", fontSize = 13.sp, color = muted)
                                }
                                LinearProgressIndicator(
                                    progress = { (r ?: 0.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (byCat.isNotEmpty()) {
            item {
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Where your effort went (30 days)", fontWeight = FontWeight.SemiBold)
                        for (c in byCat) {
                            Column {
                                Row {
                                    Text(c.first, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    Text("${c.second}", fontSize = 13.sp, color = muted)
                                }
                                LinearProgressIndicator(
                                    progress = { c.second.toFloat() / maxCat },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            DfCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Time reality", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ratio == null) {
                            "Time a few tasks with the focus timer and DayFlow will compare your estimates with reality. Your estimates are never changed automatically."
                        } else {
                            "Finished tasks took about ${Math.round(ratio * 100)}% of the time you estimated."
                        },
                        fontSize = 13.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        item { Text("Suggestions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        if (recs.isEmpty()) {
            item { EmptyState("✨", "No suggestions right now. Everything looks balanced.") }
        } else {
            items(recs.size) { i ->
                val r = recs[i]
                DfCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold)
                        Text(r.detail, fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 4.dp))
                        Row {
                            if (r.action == "rollover") {
                                TextButton(onClick = { Store.rolloverOverdue() }) { Text("Move to today") }
                            }
                            TextButton(onClick = { Store.dismissRec(r.id) }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
