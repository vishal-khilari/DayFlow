@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class TaskActions(
    val toggle: (Task) -> Unit,
    val edit: (Task) -> Unit,
    val addSub: (Task) -> Unit,
    val delete: (Task) -> Unit,
    val focus: (Task) -> Unit
)

@Composable
fun DfCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
fun EmptyState(emoji: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun CheckCircle(checked: Boolean, color: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val fill by animateColorAsState(if (checked) GreenC else Color.Transparent, label = "check")
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (checked) GreenC else color, CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ProgressRing(progress: Float, dia: Dp, label: String) {
    val anim by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (anim > 0f) {
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                    topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    DfCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BarChart(values: List<Int>, labels: List<String>, color: Color) {
    val maxV = maxOf(1, values.maxOrNull() ?: 1)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            val h by animateDpAsState(targetValue = (4f + 80f * v / maxV).dp, animationSpec = tween(600), label = "bar")
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text("$v", fontSize = 11.sp, color = muted)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(h)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (v > 0) color else color.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[i], fontSize = 11.sp, color = muted)
            }
        }
    }
}

@Composable
fun MetaRow(task: Task) {
    val td = todayStr()
    val d = task.due
    val tm = task.time
    Row(
        modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (d != null) {
            Pill(dueLabel(d), if (task.isOverdue()) RedC else if (d == td) BlueC else GreyC)
        }
        if (tm != null) {
            Pill(if (task.durationMin > 0) tm + " · " + fmtMin(task.durationMin) else tm, BlueC)
        } else if (task.durationMin > 0) {
            Pill(fmtMin(task.durationMin), GreyC)
        }
        if (task.category.isNotBlank()) Pill(task.category, GreyC)
        if (task.priority >= 2) Pill(PriorityNames[task.priority], priorityColor(task.priority))
        if (task.remind) Pill("🔔", GreyC)
    }
}

@Composable
fun TaskLine(task: Task, actions: TaskActions, extra: String? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val done = task.status == 1
    DfCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(done, priorityColor(task.priority)) { actions.toggle(task) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable { actions.edit(task) }) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) muted else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (extra != null) Text(extra, fontSize = 12.sp, color = muted)
                MetaRow(task)
            }
        }
    }
}

@Composable
fun HabitControl(h: Habit, date: LocalDate) {
    val ds = date.toString()
    val v = h.entries[ds] ?: 0
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (ds in h.skipped) {
        Text("Skipped", fontSize = 13.sp, color = muted)
    } else if (!h.scheduledOn(date)) {
        Text("Not scheduled", fontSize = 13.sp, color = muted)
    } else if (h.type == 0) {
        CheckCircle(v >= h.target, MaterialTheme.colorScheme.primary) {
            Store.setHabitEntry(h.id, ds, if (v >= 1) 0 else 1)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { if (v > 0) Store.setHabitEntry(h.id, ds, v - 1) }) { Text("−", fontSize = 18.sp) }
            Text(
                "$v/${h.target}" + (if (h.unit.isNotBlank()) " " + h.unit else ""),
                fontSize = 13.sp,
                color = if (v >= h.target) GreenC else Color.Unspecified,
                fontWeight = FontWeight.Medium
            )
            androidx.compose.material3.TextButton(onClick = { Store.setHabitEntry(h.id, ds, v + 1) }) { Text("+", fontSize = 18.sp) }
        }
    }
}
