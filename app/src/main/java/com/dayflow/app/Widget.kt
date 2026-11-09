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

val TaskIdKey = ActionParameters.Key<String>("taskId")
val HabitIdKey = ActionParameters.Key<String>("habitId")

private class WC(val bg: Color, val text: Color, val muted: Color, val track: Color, val accent: Color)

private fun widgetColors(): WC {
    val acc = Accents[Store.prefs.accent.coerceIn(0, Accents.size - 1)]
    return when (Store.prefs.widgetTheme) {
        1 -> WC(Color.White, Color(0xFF12141A), Color(0xFF5B6478), Color(0xFFE3E6EF), acc)
        2 -> WC(acc, Color.White, Color(0xCCFFFFFF), Color(0x55FFFFFF), Color.White)
        else -> WC(Color(0xFF181B22), Color(0xFFF1F3F8), Color(0xFF9AA3B5), Color(0xFF2E3442), acc)
    }
}

// ---------------------------------------------------------------- actions

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        Store.init(context)
        Store.markDone(id)
        DayFlowWidget().updateAll(context)
        HabitsWidget().updateAll(context)
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        Store.init(context)
        Store.cycleHabit(id, todayStr())
        DayFlowWidget().updateAll(context)
        HabitsWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        DayFlowWidget().updateAll(context)
        HabitsWidget().updateAll(context)
    }
}

private fun openApp(context: Context, extra: String? = null): Action {
    val intent = Intent(context, MainActivity::class.java)
    intent.action = "com.dayflow.app.WIDGET_" + (extra ?: "OPEN")
    if (extra != null) intent.putExtra("action", extra)
    return actionStartActivity(intent)
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun WText(
    text: String,
    size: Int,
    color: Color,
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
private fun WBar(frac: Float, wc: WC, thickness: Int) {
    LinearProgressIndicator(
        progress = frac,
        modifier = GlanceModifier.fillMaxWidth().height(thickness.dp),
        color = ColorProvider(wc.accent),
        backgroundColor = ColorProvider(wc.track)
    )
}

@Composable
private fun WidgetTask(t: Task, wc: WC) {
    val tm = t.time
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WText(
            "○", 22, wc.accent, true,
            GlanceModifier.padding(end = 8.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id)))
        )
        Column {
            WText(t.title, 13, wc.text, true)
            if (tm != null) WText(fmtHHmm(tm), 11, wc.muted)
        }
    }
}

@Composable
private fun WidgetHabitIcons(habits: List<Habit>, wc: WC, td: String, max: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (h in habits.take(max)) {
            val v = h.entries[td] ?: 0
            val ok = v >= h.target
            WText(
                h.icon + (if (ok) "✓" else "○") + "  ",
                16,
                if (ok) wc.accent else wc.muted,
                ok,
                GlanceModifier.clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id)))
            )
        }
    }
}

// ---------------------------------------------------------------- Today widget

class DayFlowWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 250.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent { TodayContent(context) }
    }
}

class DayFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayFlowWidget()
}

@Composable
private fun TodayContent(context: Context) {
    val size = LocalSize.current
    val wc = widgetColors()
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
    val habitsDone = habitsToday.count { (it.entries[td] ?: 0) >= it.target }

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(wc.bg))
        .cornerRadius(20.dp)
        .padding(12.dp)
        .clickable(openApp(context))

    val tall = size.height >= 150.dp
    val wide = size.width >= 200.dp

    Column(modifier = root) {
        if (tall && size.height >= 220.dp) {
            // ----- large -----
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(dateText, 12, wc.muted)
                    WText("$done/$total done", 18, wc.text, true)
                }
                WText("＋", 24, wc.accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            WBar(frac, wc, 6)
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (open.isEmpty()) {
                WText("All clear for today 🎉", 13, wc.muted)
            } else {
                for (t in open.take(3)) WidgetTask(t, wc)
            }
            if (habitsToday.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                WText("Habits $habitsDone/${habitsToday.size}", 11, wc.muted)
                WidgetHabitIcons(habitsToday, wc, td, 5)
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            WText(
                if (next != null) "Next: " + fmtHHmm(next.time ?: "") .ifBlank { "anytime" } + "  " + next.title else "Nothing coming up",
                12, wc.muted
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("＋ Add", 13, wc.accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
                Spacer(modifier = GlanceModifier.width(16.dp))
                WText("▶ Focus", 13, wc.accent, true, GlanceModifier.clickable(openApp(context, "focus")))
                Spacer(modifier = GlanceModifier.width(16.dp))
                WText("⟳", 15, wc.accent, true, GlanceModifier.clickable(actionRunCallback<RefreshAction>()))
            }
        } else if (tall && wide) {
            // ----- medium -----
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(dateText, 12, wc.muted)
                    WText("$done/$total done", 16, wc.text, true)
                }
                WText("＋", 24, wc.accent, true, GlanceModifier.clickable(openApp(context, "quickAdd")))
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            WBar(frac, wc, 5)
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (open.isEmpty()) {
                WText("All clear for today 🎉", 13, wc.muted)
            } else {
                for (t in open.take(2)) WidgetTask(t, wc)
            }
            if (habitsToday.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                WidgetHabitIcons(habitsToday, wc, td, 6)
            }
        } else if (wide) {
            // ----- wide and short: two columns -----
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(dateText, 11, wc.muted)
                    WText("$done/$total", 22, wc.text, true)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    WBar(frac, wc, 5)
                    if (habitsToday.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        WidgetHabitIcons(habitsToday, wc, td, 4)
                    }
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (open.isEmpty()) {
                        WText("All clear 🎉", 13, wc.muted)
                    } else {
                        for (t in open.take(2)) WidgetTask(t, wc)
                    }
                }
            }
        } else {
            // ----- small -----
            WText(dateText, 12, wc.muted)
            WText("$done/$total", 26, wc.text, true)
            WText("tasks done", 11, wc.muted)
            Spacer(modifier = GlanceModifier.height(6.dp))
            WBar(frac, wc, 5)
            Spacer(modifier = GlanceModifier.height(6.dp))
            WText(if (next != null) next.title else "Nothing next", 12, wc.text)
            if (habitsToday.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                WText("🔥 habits $habitsDone/${habitsToday.size}", 11, wc.muted)
            }
        }
    }
}

// ---------------------------------------------------------------- Habits widget

class HabitsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 250.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent { HabitsContent(context) }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

@Composable
private fun HabitsContent(context: Context) {
    val size = LocalSize.current
    val wc = widgetColors()
    val td = todayStr()
    val habits = Store.habits.filter { it.scheduledOn(LocalDate.now()) && td !in it.skipped }
    val done = habits.count { (it.entries[td] ?: 0) >= it.target }
    val frac = if (habits.isEmpty()) 0f else done.toFloat() / habits.size
    val bestStreak = habits.maxOfOrNull { it.currentStreak() } ?: 0

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(wc.bg))
        .cornerRadius(20.dp)
        .padding(12.dp)
        .clickable(openApp(context, "habits"))

    Column(modifier = root) {
        if (habits.isEmpty()) {
            WText("Habits", 14, wc.muted)
            WText("No habits today", 15, wc.text, true)
            WText("Tap to add one", 11, wc.muted)
        } else if (size.height >= 200.dp) {
            // ----- large list -----
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText("Habits today", 12, wc.muted)
                    WText("$done/${habits.size} done", 18, wc.text, true)
                }
                WText("🔥 $bestStreak", 14, wc.accent, true)
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            WBar(frac, wc, 6)
            Spacer(modifier = GlanceModifier.height(6.dp))
            for (h in habits.take(6)) {
                val v = h.entries[td] ?: 0
                val ok = v >= h.target
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WText(h.icon, 18, wc.text)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WText(h.name, 13, wc.text, true)
                        val sub = "🔥 ${h.currentStreak()}" + (if (h.type == 1) "  ·  $v/${h.target} ${h.unit}".trimEnd() else "")
                        WText(sub, 11, wc.muted)
                    }
                    WText(if (ok) "✓" else "○", 20, if (ok) wc.accent else wc.muted, true)
                }
            }
        } else if (size.width >= 200.dp) {
            // ----- wide row of icons -----
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("Habits $done/${habits.size}", 15, wc.text, true, GlanceModifier.defaultWeight())
                WText("🔥 $bestStreak", 13, wc.accent, true)
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            WBar(frac, wc, 5)
            Spacer(modifier = GlanceModifier.height(8.dp))
            WidgetHabitIcons(habits, wc, td, 6)
        } else {
            // ----- small summary -----
            WText("Habits", 12, wc.muted)
            WText("$done/${habits.size}", 26, wc.text, true)
            Spacer(modifier = GlanceModifier.height(6.dp))
            WBar(frac, wc, 5)
            Spacer(modifier = GlanceModifier.height(6.dp))
            WText("🔥 best streak $bestStreak", 11, wc.muted)
        }
    }
}
