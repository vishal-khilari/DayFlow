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
    val remindAt: Long = 0L,
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
    val startDate: String = LocalDate.now().toString(),
    val remindTime: String? = null
)

data class FocusSession(val date: String, val minutes: Int, val taskId: String?, val taskTitle: String)

data class Prefs(
    val name: String = "",
    val themeMode: Int = 0,
    val accent: Int = 0,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val longBreakMin: Int = 15,
    val dayStart: Int = 7,
    val dayEnd: Int = 22,
    val snoozeMin: Int = 10,
    val hidden: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet(),
    val use24h: Boolean = false,
    val weekStartMon: Boolean = true,
    val defaultDuration: Int = 0,
    val defaultPriority: Int = 1,
    val defaultRemindMode: Int = 0,
    val textScale: Int = 1,
    val haptics: Boolean = true,
    val startTab: Int = 0,
    val widgetTheme: Int = 0,
    val categories: List<String> = listOf("Study", "Work", "Health", "Personal", "Other"),
    val morningOn: Boolean = false,
    val morningTime: String = "08:00",
    val eveningOn: Boolean = false,
    val eveningTime: String = "21:00"
)

data class Rec(val id: String, val title: String, val detail: String, val action: String? = null)

data class Review(val date: String, val mood: Int, val wins: String, val blockers: String, val tomorrow: String)

data class Template(val name: String, val emoji: String, val category: String, val items: List<Pair<String, Int>>)

val Templates = listOf(
    Template("Morning routine", "🌅", "Personal", listOf("Drink water" to 5, "Stretch" to 10, "Plan the day" to 10, "Healthy breakfast" to 20)),
    Template("Evening routine", "🌙", "Personal", listOf("Tidy up" to 10, "Pack tomorrow's bag" to 10, "Review the day" to 10, "Wind down, no screens" to 30)),
    Template("Study session", "📚", "Study", listOf("Review notes" to 20, "Learn a new topic" to 45, "Practice problems" to 30, "Summarise what I learned" to 10)),
    Template("Gym session", "💪", "Health", listOf("Warm-up" to 10, "Main workout" to 40, "Cool-down and stretch" to 10)),
    Template("Weekly planning", "🗓️", "Work", listOf("Review last week" to 15, "Pick the top 3 goals" to 10, "Schedule key tasks" to 20)),
    Template("Job application", "💼", "Work", listOf("Tailor my CV" to 30, "Write the cover letter" to 30, "Submit the application" to 10, "Follow up after a week" to 5)),
    Template("Exam preparation", "🎓", "Study", listOf("Collect the syllabus" to 15, "Make a revision plan" to 20, "Revise topic 1" to 60, "Revise topic 2" to 60, "Mock test" to 90))
)

fun categoryEmoji(c: String): String = when (c) {
    "Study" -> "📚"
    "Work" -> "💼"
    "Health" -> "💪"
    "Personal" -> "🏡"
    "Other" -> "📌"
    "" -> ""
    else -> "🏷️"
}

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
    val h = mm / 60
    val m = mm % 60
    return if (Store.prefs.use24h) {
        "%02d:%02d".format(h, m)
    } else {
        val h12 = if (h % 12 == 0) 12 else h % 12
        "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}

/** Turns a stored "HH:mm" (24h) string into the user's chosen display format. */
fun fmtHHmm(s: String): String {
    val p = s.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return s
    val m = p.getOrNull(1)?.toIntOrNull() ?: return s
    return fmtClock(h * 60 + m)
}

fun toHHmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun parseTime(s: String?): java.time.LocalTime {
    return try {
        java.time.LocalTime.parse(s ?: "09:00")
    } catch (e: Exception) {
        java.time.LocalTime.of(9, 0)
    }
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
    o.put("remindAt", t.remindAt)
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
    remindAt = o.optLong("remindAt", 0L),
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
    o.put("remindTime", h.remindTime ?: JSONObject.NULL)
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
        startDate = o.optString("startDate", LocalDate.now().toString()),
        remindTime = o.optS("remindTime")
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
    o.put("dayStart", p.dayStart)
    o.put("dayEnd", p.dayEnd)
    o.put("snoozeMin", p.snoozeMin)
    o.put("hidden", JSONArray(p.hidden.toList()))
    o.put("dismissed", JSONArray(p.dismissed.toList()))
    o.put("use24h", p.use24h)
    o.put("weekStartMon", p.weekStartMon)
    o.put("defaultDuration", p.defaultDuration)
    o.put("defaultPriority", p.defaultPriority)
    o.put("defaultRemindMode", p.defaultRemindMode)
    o.put("textScale", p.textScale)
    o.put("haptics", p.haptics)
    o.put("startTab", p.startTab)
    o.put("widgetTheme", p.widgetTheme)
    o.put("categories", JSONArray(p.categories))
    o.put("morningOn", p.morningOn)
    o.put("morningTime", p.morningTime)
    o.put("eveningOn", p.eveningOn)
    o.put("eveningTime", p.eveningTime)
    return o
}

fun prefsFromJson(o: JSONObject): Prefs {
    val d = Prefs()
    val cats = o.optJSONArray("categories")?.strings() ?: d.categories
    return Prefs(
        name = o.optString("name", ""),
        themeMode = o.optInt("themeMode", 0),
        accent = o.optInt("accent", 0),
        focusMin = o.optInt("focusMin", 25),
        breakMin = o.optInt("breakMin", 5),
        longBreakMin = o.optInt("longBreakMin", 15),
        dayStart = o.optInt("dayStart", 7),
        dayEnd = o.optInt("dayEnd", 22),
        snoozeMin = o.optInt("snoozeMin", 10),
        hidden = (o.optJSONArray("hidden")?.strings() ?: emptyList()).toSet(),
        dismissed = (o.optJSONArray("dismissed")?.strings() ?: emptyList()).toSet(),
        use24h = o.optBoolean("use24h", false),
        weekStartMon = o.optBoolean("weekStartMon", true),
        defaultDuration = o.optInt("defaultDuration", 0),
        defaultPriority = o.optInt("defaultPriority", 1),
        defaultRemindMode = o.optInt("defaultRemindMode", 0),
        textScale = o.optInt("textScale", 1),
        haptics = o.optBoolean("haptics", true),
        startTab = o.optInt("startTab", 0),
        widgetTheme = o.optInt("widgetTheme", 0),
        categories = cats,
        morningOn = o.optBoolean("morningOn", false),
        morningTime = o.optString("morningTime", "08:00"),
        eveningOn = o.optBoolean("eveningOn", false),
        eveningTime = o.optString("eveningTime", "21:00")
    )
}

fun reviewToJson(r: Review): JSONObject {
    val o = JSONObject()
    o.put("date", r.date)
    o.put("mood", r.mood)
    o.put("wins", r.wins)
    o.put("blockers", r.blockers)
    o.put("tomorrow", r.tomorrow)
    return o
}

fun reviewFromJson(o: JSONObject): Review = Review(
    date = o.getString("date"),
    mood = o.optInt("mood", 2),
    wins = o.optString("wins", ""),
    blockers = o.optString("blockers", ""),
    tomorrow = o.optString("tomorrow", "")
)
