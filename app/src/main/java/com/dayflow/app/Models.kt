package com.dayflow.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** status: 0 = open, 1 = done, 2 = skipped. priority: 0 low, 1 medium, 2 high, 3 urgent. */
data class Task(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val priority: Int = 1,
    val due: String? = null,
    val time: String? = null,
    val durationMin: Int = 0,
    val status: Int = 0,
    val doneOn: String? = null,
    val remind: Boolean = false,
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val order: Long = 0L
)

/** type: 0 = yes/no, 1 = count / number (with a unit such as glasses, pages, minutes). days: ISO weekdays 1..7. */
data class Habit(
    val id: String,
    val name: String,
    val icon: String = "✅",
    val type: Int = 0,
    val target: Int = 1,
    val unit: String = "",
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val entries: Map<String, Int> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    val startDate: String = LocalDate.now().toString()
)

data class FocusSession(val date: String, val minutes: Int, val taskId: String?, val taskTitle: String)

data class Prefs(
    val name: String = "",
    val themeMode: Int = 0,
    val accent: Int = 0,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val longBreakMin: Int = 15,
    val dayEnd: Int = 22,
    val snoozeMin: Int = 10,
    val hidden: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet()
)

data class Rec(val id: String, val title: String, val detail: String, val action: String? = null)

// ---------- date helpers ----------

fun today(): LocalDate = LocalDate.now()
fun todayStr(): String = LocalDate.now().toString()

fun parseDate(s: String?): LocalDate? {
    if (s == null) return null
    return try {
        LocalDate.parse(s)
    } catch (e: Exception) {
        null
    }
}

fun fmtMin(m: Int): String {
    return if (m >= 60) {
        if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
    } else {
        "${m}m"
    }
}

fun fmtClock(minutes: Int): String {
    val mm = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(mm / 60, mm % 60)
}

fun startMinutes(t: Task): Int? {
    val s = t.time ?: return null
    val p = s.split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return h * 60 + m
}

fun overlaps(a: Task, b: Task): Boolean {
    val sa = startMinutes(a) ?: return false
    val sb = startMinutes(b) ?: return false
    val ea = sa + max(a.durationMin, 1)
    val eb = sb + max(b.durationMin, 1)
    return sa < eb && sb < ea
}

fun Task.isOverdue(): Boolean {
    val d = due ?: return false
    return status == 0 && d < todayStr()
}

fun dueLabel(d: String): String {
    val date = parseDate(d) ?: return d
    val now = LocalDate.now()
    return when {
        date == now -> "Today"
        date == now.plusDays(1) -> "Tomorrow"
        date == now.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

// ---------- habit logic ----------

fun Habit.scheduledOn(d: LocalDate): Boolean = d.dayOfWeek.value in days && d.toString() >= startDate
fun Habit.doneOn(d: LocalDate): Boolean = (entries[d.toString()] ?: 0) >= target

fun Habit.currentStreak(): Int {
    var d = LocalDate.now()
    var streak = 0
    var guard = 0
    while (guard < 3650 && d.toString() >= startDate) {
        guard++
        val skippedDay = d.toString() in skipped
        if (!scheduledOn(d) || skippedDay) {
            // neutral day: does not add to or break the streak
        } else if (doneOn(d)) {
            streak++
        } else if (d != LocalDate.now()) {
            break
        }
        d = d.minusDays(1)
    }
    return streak
}

fun Habit.longestStreak(): Int {
    val start = parseDate(startDate) ?: return 0
    val end = LocalDate.now()
    var d = start
    var cur = 0
    var best = 0
    while (!d.isAfter(end)) {
        if (scheduledOn(d) && d.toString() !in skipped) {
            if (doneOn(d)) {
                cur++
                if (cur > best) best = cur
            } else if (d != end) {
                cur = 0
            }
        }
        d = d.plusDays(1)
    }
    return best
}

/** Completion rate over [n] days, starting [offset] days ago. Skipped and unscheduled days are ignored; today only counts once done. */
fun Habit.rate(n: Int, offset: Int = 0): Double? {
    var scheduled = 0
    var done = 0
    for (i in offset until offset + n) {
        val d = LocalDate.now().minusDays(i.toLong())
        if (!scheduledOn(d) || d.toString() in skipped) continue
        if (i == 0 && !doneOn(d)) continue
        scheduled++
        if (doneOn(d)) done++
    }
    return if (scheduled == 0) null else done.toDouble() / scheduled
}

fun Habit.totalCompletions(): Int = entries.count { it.value >= target }

// ---------- JSON ----------

fun JSONObject.optS(k: String): String? = if (isNull(k)) null else optString(k)
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

fun taskToJson(t: Task): JSONObject {
    val o = JSONObject()
    o.put("id", t.id)
    o.put("parentId", t.parentId ?: JSONObject.NULL)
    o.put("title", t.title)
    o.put("notes", t.notes)
    o.put("priority", t.priority)
    o.put("due", t.due ?: JSONObject.NULL)
    o.put("time", t.time ?: JSONObject.NULL)
    o.put("durationMin", t.durationMin)
    o.put("status", t.status)
    o.put("doneOn", t.doneOn ?: JSONObject.NULL)
    o.put("remind", t.remind)
    o.put("category", t.category)
    o.put("createdAt", t.createdAt)
    o.put("order", t.order)
    return o
}

fun taskFromJson(o: JSONObject): Task = Task(
    id = o.getString("id"),
    parentId = o.optS("parentId"),
    title = o.optString("title", ""),
    notes = o.optString("notes", ""),
    priority = o.optInt("priority", 1),
    due = o.optS("due"),
    time = o.optS("time"),
    durationMin = o.optInt("durationMin", 0),
    status = o.optInt("status", 0),
    doneOn = o.optS("doneOn"),
    remind = o.optBoolean("remind", false),
    category = o.optString("category", ""),
    createdAt = o.optLong("createdAt", 0L),
    order = o.optLong("order", 0L)
)

fun habitToJson(h: Habit): JSONObject {
    val o = JSONObject()
    o.put("id", h.id)
    o.put("name", h.name)
    o.put("icon", h.icon)
    o.put("type", h.type)
    o.put("target", h.target)
    o.put("unit", h.unit)
    o.put("days", JSONArray(h.days))
    o.put("startDate", h.startDate)
    val e = JSONObject()
    for ((k, v) in h.entries) e.put(k, v)
    o.put("entries", e)
    o.put("skipped", JSONArray(h.skipped.toList()))
    return o
}

fun habitFromJson(o: JSONObject): Habit {
    val map = mutableMapOf<String, Int>()
    val eo = o.optJSONObject("entries")
    if (eo != null) {
        for (k in eo.keys()) map[k] = eo.getInt(k)
    }
    val days = o.optJSONArray("days")?.ints() ?: listOf(1, 2, 3, 4, 5, 6, 7)
    val skipped = o.optJSONArray("skipped")?.strings() ?: emptyList()
    return Habit(
        id = o.getString("id"),
        name = o.optString("name", ""),
        icon = o.optString("icon", "✅"),
        type = o.optInt("type", 0),
        target = o.optInt("target", 1),
        unit = o.optString("unit", ""),
        days = days,
        entries = map,
        skipped = skipped.toSet(),
        startDate = o.optString("startDate", LocalDate.now().toString())
    )
}

fun sessionToJson(s: FocusSession): JSONObject {
    val o = JSONObject()
    o.put("date", s.date)
    o.put("minutes", s.minutes)
    o.put("taskId", s.taskId ?: JSONObject.NULL)
    o.put("taskTitle", s.taskTitle)
    return o
}

fun sessionFromJson(o: JSONObject): FocusSession = FocusSession(
    date = o.getString("date"),
    minutes = o.optInt("minutes", 0),
    taskId = o.optS("taskId"),
    taskTitle = o.optString("taskTitle", "")
)

fun prefsToJson(p: Prefs): JSONObject {
    val o = JSONObject()
    o.put("name", p.name)
    o.put("themeMode", p.themeMode)
    o.put("accent", p.accent)
    o.put("focusMin", p.focusMin)
    o.put("breakMin", p.breakMin)
    o.put("longBreakMin", p.longBreakMin)
    o.put("dayEnd", p.dayEnd)
    o.put("snoozeMin", p.snoozeMin)
    o.put("hidden", JSONArray(p.hidden.toList()))
    o.put("dismissed", JSONArray(p.dismissed.toList()))
    return o
}

fun prefsFromJson(o: JSONObject): Prefs = Prefs(
    name = o.optString("name", ""),
    themeMode = o.optInt("themeMode", 0),
    accent = o.optInt("accent", 0),
    focusMin = o.optInt("focusMin", 25),
    breakMin = o.optInt("breakMin", 5),
    longBreakMin = o.optInt("longBreakMin", 15),
    dayEnd = o.optInt("dayEnd", 22),
    snoozeMin = o.optInt("snoozeMin", 10),
    hidden = (o.optJSONArray("hidden")?.strings() ?: emptyList()).toSet(),
    dismissed = (o.optJSONArray("dismissed")?.strings() ?: emptyList()).toSet()
)
