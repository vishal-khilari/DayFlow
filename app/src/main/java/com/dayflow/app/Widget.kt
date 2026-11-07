package com.dayflow.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ColBg = Color(0xFF181B22)
private val ColText = Color(0xFFF1F3F8)
private val ColMuted = Color(0xFF9AA3B5)
private val ColTrack = Color(0xFF2E3442)

val TaskIdKey = ActionParameters.Key<String>("taskId")
val HabitIdKey = ActionParameters.Key<String>("habitId")

class DayFlowWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 250.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent {
            WidgetContent(context)
        }
    }
}

class DayFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayFlowWidget()
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        Store.init(context)
        Store.markDone(id)
        DayFlowWidget().updateAll(context)
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        Store.init(context)
        Store.cycleHabit(id, todayStr())
        DayFlowWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        DayFlowWidget().updateAll(context)
    }
}

private fun openApp(context: Context, extra: String? = null): Action {
    val intent = Intent(context, MainActivity::class.java)
    intent.action = "com.dayflow.app.WIDGET_" + (extra ?: "OPEN")
    if (extra != null) intent.putExtra("action", extra)
    return actionStartActivity(intent)
}

@Composable
private fun WText(
    text: String,
    size: Int = 13,
    color: Color = ColText,
    bold: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = ColorProvider(color),
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        ),
        maxLines = 1
    )
}

@Composable
private fun WidgetContent(context: Context) {
    val size = LocalSize.current
    val accent = Accents[Store.prefs.accent.coerceIn(0, Accents.size - 1)]
    val td = todayStr()
    val eligible = Store.eligibleToday()
    val total = eligible.size
    val done = eligible.count { it.status == 1 }
    val frac = if (total == 0) 0f else done.toFloat() / total
    val open = eligible.filter { it.status == 0 }
        .sortedWith(compareBy<Task>({ it.time ?: "99:99" }, { -it.priority }))
    val now = LocalTime.now()
    val nowStr = "%02d:%02d".format(now.hour, now.minute)
    val next = open.firstOrNull { val tm = it.time; tm != null && tm >= nowStr } ?: open.firstOrNull()
    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM"))
    val habitsToday = Store.habits.filter { it.scheduledOn(LocalDate.now()) && td !in it.skipped }

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(ColBg))
        .cornerRadius(20.dp)
        .padding(12.dp)
        .clickable(openApp(context))

    Column(modifier = root) {
        if (size.height >= 250.dp) {
            // ---------- large ----------
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(dateText, 12, ColMuted)
                    WText("$done/$total done", 18, ColText, true)
                }
                WText("＋", 22, accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ColorProvider(accent),
                backgroundColor = ColorProvider(ColTrack)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (open.isEmpty()) {
                WText("All clear for today 🎉", 13, ColMuted)
            } else {
                for (t in open.take(3)) WidgetTask(t, accent)
            }
            if (habitsToday.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (h in habitsToday.take(4)) {
                        val v = h.entries[td] ?: 0
                        WText(
                            h.icon + (if (v >= h.target) "✓" else "○") + "  ",
                            16,
                            if (v >= h.target) accent else ColMuted,
                            false,
                            GlanceModifier.clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id)))
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            WText(if (next != null) "Next: " + (next.time ?: "anytime") + "  " + next.title else "Nothing coming up", 12, ColMuted)
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("＋ Add", 13, accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
                Spacer(modifier = GlanceModifier.width(16.dp))
                WText("▶ Focus", 13, accent, true, GlanceModifier.clickable(openApp(context, "focus")))
                Spacer(modifier = GlanceModifier.width(16.dp))
                WText("⟳ Refresh", 13, accent, true, GlanceModifier.clickable(actionRunCallback<RefreshAction>()))
            }
        } else if (size.width >= 250.dp) {
            // ---------- medium ----------
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(dateText, 12, ColMuted)
                    WText("$done/$total done", 16, ColText, true)
                }
                WText("＋", 22, accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = ColorProvider(accent),
                backgroundColor = ColorProvider(ColTrack)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (open.isEmpty()) {
                WText("All clear for today 🎉", 13, ColMuted)
            } else {
                for (t in open.take(2)) WidgetTask(t, accent)
            }
        } else {
            // ---------- small ----------
            WText(dateText, 12, ColMuted)
            WText("$done/$total", 26, ColText, true)
            WText("tasks done", 11, ColMuted)
            Spacer(modifier = GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = ColorProvider(accent),
                backgroundColor = ColorProvider(ColTrack)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            WText(if (next != null) next.title else "Nothing next", 12, ColText)
        }
    }
}

@Composable
private fun WidgetTask(t: Task, accent: Color) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WText(
            "○",
            22,
            accent,
            true,
            GlanceModifier.padding(end = 8.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id)))
        )
        val tm = t.time
        Column {
            WText(t.title, 13, ColText, true)
            if (tm != null) WText(tm, 11, ColMuted)
        }
    }
}
