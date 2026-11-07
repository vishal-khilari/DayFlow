package com.dayflow.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Single source of truth. Everything is saved to SharedPreferences on the phone. */
object Store {
    var tasks by mutableStateOf(listOf<Task>())
        private set
    var habits by mutableStateOf(listOf<Habit>())
        private set
    var sessions by mutableStateOf(listOf<FocusSession>())
        private set
    var prefs by mutableStateOf(Prefs())
        private set

    private var appCtx: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        appCtx = context.applicationContext
        reload()
    }

    private fun sp() = appCtx!!.getSharedPreferences("dayflow", Context.MODE_PRIVATE)

    private fun <T> readList(s: String?, f: (JSONObject) -> T): List<T> {
        if (s == null) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { f(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun reload() {
        if (appCtx == null) return
        val p = sp()
        tasks = readList(p.getString("tasks", null)) { taskFromJson(it) }
        habits = readList(p.getString("habits", null)) { habitFromJson(it) }
        sessions = readList(p.getString("sessions", null)) { sessionFromJson(it) }
        val ps = p.getString("prefs", null)
        prefs = try {
            if (ps == null) Prefs() else prefsFromJson(JSONObject(ps))
        } catch (e: Exception) {
            Prefs()
        }
    }

    private fun refreshWidgets() {
        val c = appCtx ?: return
        scope.launch {
            try {
                DayFlowWidget().updateAll(c)
            } catch (e: Exception) {
                // widget not placed yet - nothing to do
            }
        }
    }

    private fun saveTasks(list: List<Task>) {
        tasks = list
        sp().edit().putString("tasks", JSONArray(list.map { taskToJson(it) }).toString()).apply()
        refreshWidgets()
    }

    private fun saveHabits(list: List<Habit>) {
        habits = list
        sp().edit().putString("habits", JSONArray(list.map { habitToJson(it) }).toString()).apply()
        refreshWidgets()
    }

    private fun saveSessions(list: List<FocusSession>) {
        sessions = list
        sp().edit().putString("sessions", JSONArray(list.map { sessionToJson(it) }).toString()).apply()
        refreshWidgets()
    }

    fun updatePrefs(p: Prefs) {
        prefs = p
        sp().edit().putString("prefs", prefsToJson(p).toString()).apply()
        refreshWidgets()
    }

    // ---------- tasks ----------

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun nextOrder(): Long = max(System.currentTimeMillis(), (tasks.maxOfOrNull { it.order } ?: 0L) + 1L)

    fun addTask(t: Task) {
        saveTasks(tasks + t)
        appCtx?.let { Reminders.schedule(it, t) }
    }

    fun updateTask(t: Task) {
        saveTasks(tasks.map { if (it.id == t.id) t else it })
        appCtx?.let { Reminders.schedule(it, t) }
    }

    fun childrenOf(parent: String?): List<Task> = tasks.filter { it.parentId == parent }.sortedBy { it.order }

    fun descendants(id: String): List<Task> {
        val out = mutableListOf<Task>()
        fun walk(p: String) {
            for (c in tasks.filter { it.parentId == p }) {
                out.add(c)
                walk(c.id)
            }
        }
        walk(id)
        return out
    }

    fun subtreeAny(t: Task, pred: (Task) -> Boolean): Boolean {
        if (pred(t)) return true
        return descendants(t.id).any(pred)
    }

    /** (completed, total) over every nested subtask, skipped ones excluded. */
    fun subProgress(id: String): Pair<Int, Int> {
        val d = descendants(id).filter { it.status != 2 }
        return Pair(d.count { it.status == 1 }, d.size)
    }

    /** Toggles one task only. Parents never complete automatically. */
    fun toggle(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        val nt = if (t.status == 0) t.copy(status = 1, doneOn = todayStr()) else t.copy(status = 0, doneOn = null)
        updateTask(nt)
    }

    fun markDone(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.status == 0) updateTask(t.copy(status = 1, doneOn = todayStr()))
    }

    fun setStatus(id: String, status: Int) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        updateTask(t.copy(status = status, doneOn = if (status == 1) todayStr() else null))
    }

    fun completeTree(id: String) {
        val ids = descendants(id).map { it.id }.toSet() + id
        val td = todayStr()
        saveTasks(tasks.map { if (it.id in ids && it.status == 0) it.copy(status = 1, doneOn = td) else it })
        appCtx?.let { c -> ids.forEach { Reminders.cancel(c, it) } }
    }

    fun deleteTree(id: String) {
        val ids = descendants(id).map { it.id }.toSet() + id
        appCtx?.let { c -> ids.forEach { Reminders.cancel(c, it) } }
        saveTasks(tasks.filter { it.id !in ids })
    }

    fun promote(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        updateTask(t.copy(parentId = null))
    }

    fun move(id: String, delta: Int) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        val sibs = childrenOf(t.parentId)
        val i = sibs.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= sibs.size) return
        val a = sibs[i]
        val b = sibs[j]
        saveTasks(tasks.map {
            when (it.id) {
                a.id -> it.copy(order = b.order)
                b.id -> it.copy(order = a.order)
                else -> it
            }
        })
    }

    fun rolloverOverdue() {
        val td = todayStr()
        val updated = tasks.map { if (it.isOverdue()) it.copy(due = td) else it }
        saveTasks(updated)
        appCtx?.let { c -> updated.forEach { Reminders.schedule(c, it) } }
    }

    /** Tasks counted in today's progress: due today or overdue and still open, plus anything finished today that was due today or earlier. */
    fun eligibleToday(): List<Task> {
        val td = todayStr()
        return tasks.filter { t ->
            val d = t.due
            d != null && d <= td && (t.status == 0 || (t.status == 1 && t.doneOn == td))
        }
    }

    // ---------- habits ----------

    fun addHabit(h: Habit) = saveHabits(habits + h)

    fun updateHabit(h: Habit) = saveHabits(habits.map { if (it.id == h.id) h.copy(entries = it.entries, skipped = it.skipped) else it })

    fun deleteHabit(id: String) = saveHabits(habits.filter { it.id != id })

    fun setHabitEntry(id: String, date: String, value: Int) {
        saveHabits(habits.map {
            if (it.id == id) {
                it.copy(entries = if (value <= 0) it.entries - date else it.entries + (date to value))
            } else it
        })
    }

    /** Used by the widget: yes/no habits toggle, count habits go up by one and wrap to zero after the target. */
    fun cycleHabit(id: String, date: String) {
        val h = habits.firstOrNull { it.id == id } ?: return
        val v = h.entries[date] ?: 0
        setHabitEntry(id, date, if (v >= h.target) 0 else v + 1)
    }

    fun toggleSkip(id: String, date: String) {
        saveHabits(habits.map {
            if (it.id == id) {
                it.copy(skipped = if (date in it.skipped) it.skipped - date else it.skipped + date)
            } else it
        })
    }

    // ---------- focus ----------

    fun addSession(s: FocusSession) = saveSessions(sessions + s)

    fun focusMinutes(date: String): Int = sessions.filter { it.date == date }.sumOf { it.minutes }

    fun actualMinutes(taskId: String): Int = sessions.filter { it.taskId == taskId }.sumOf { it.minutes }

    /** Actual / estimated minutes over finished tasks that were tracked with the timer. Null until there is enough data. */
    fun timeRealityRatio(): Double? {
        val pairs = tasks.filter { it.status == 1 && it.durationMin > 0 }
            .map { Pair(it.durationMin, actualMinutes(it.id)) }
            .filter { it.second > 0 }
        if (pairs.size < 2) return null
        return pairs.sumOf { it.second }.toDouble() / pairs.sumOf { it.first }
    }

    // ---------- score and suggestions ----------

    /** 50% tasks finished (of tasks due in the last 7 days), 30% habit consistency, 20% focus (goal 5h/week). Missing parts are left out. */
    fun weeklyScore(): Int? {
        val end = LocalDate.now()
        val start = end.minusDays(6)
        val due = tasks.filter {
            val d = parseDate(it.due)
            d != null && !d.isBefore(start) && !d.isAfter(end) && it.status != 2
        }
        var sum = 0.0
        var weight = 0.0
        if (due.isNotEmpty()) {
            sum += 50 * (due.count { it.status == 1 }.toDouble() / due.size)
            weight += 50
        }
        val rates = habits.mapNotNull { it.rate(7) }
        if (rates.isNotEmpty()) {
            sum += 30 * rates.average()
            weight += 30
        }
        var focus = 0
        for (i in 0..6) focus += focusMinutes(end.minusDays(i.toLong()).toString())
        if (focus > 0) {
            sum += 20 * min(1.0, focus / 300.0)
            weight += 20
        }
        if (weight == 0.0) return null
        return Math.round(sum / weight * 100).toInt()
    }

    fun recommendations(): List<Rec> {
        val out = mutableListOf<Rec>()
        val now = LocalDate.now()
        val td = now.toString()

        val overdue = tasks.count { it.isOverdue() }
        if (overdue > 0) {
            out.add(
                Rec(
                    "overdue:$td",
                    "$overdue overdue task" + (if (overdue == 1) "" else "s"),
                    "These were due before today and are still open. Move them to today, then reorder or skip the ones that no longer matter.",
                    "rollover"
                )
            )
        }

        val tomorrow = now.plusDays(1).toString()
        val tom = tasks.filter { it.status == 0 && it.due == tomorrow }
        val tomMin = tom.sumOf { it.durationMin }
        val capacity = max(60, (prefs.dayEnd - 7) * 60)
        if (tom.size >= 8 || tomMin > capacity * 0.8) {
            out.add(
                Rec(
                    "tomorrow:$td",
                    "Tomorrow looks overloaded",
                    "${tom.size} tasks and ${fmtMin(tomMin)} of planned work for a day with about ${fmtMin(capacity)} available. Consider moving something to another day.",
                    null
                )
            )
        }

        val cutoff = System.currentTimeMillis() - 3L * 86_400_000L
        val oldInbox = tasks.count { it.parentId == null && it.status == 0 && it.due == null && it.createdAt < cutoff }
        if (oldInbox > 0) {
            out.add(
                Rec(
                    "inbox:$td",
                    "$oldInbox inbox task" + (if (oldInbox == 1) "" else "s") + " waiting for 3+ days",
                    "Give them a date, or delete them. Open the Tasks tab and choose Inbox.",
                    null
                )
            )
        }

        val ratio = timeRealityRatio()
        if (ratio != null && ratio > 1.25) {
            val pct = Math.round((ratio - 1) * 100).toInt()
            out.add(
                Rec(
                    "reality:$td",
                    "Your tasks take about $pct% longer than planned",
                    "Based on tasks you timed with the focus timer. Try adding a buffer to new estimates.",
                    null
                )
            )
        }

        for (h in habits) {
            val thisWeek = h.rate(7)
            val lastWeek = h.rate(7, 7)
            if (thisWeek != null && lastWeek != null && lastWeek - thisWeek >= 0.25) {
                out.add(
                    Rec(
                        "habit:${h.id}:$td",
                        "${h.name} slipped this week",
                        "${Math.round(thisWeek * 100)}% this week versus ${Math.round(lastWeek * 100)}% last week. A smaller target for a few days can help.",
                        null
                    )
                )
            }
        }

        val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val planned = tasks.filter { it.status == 0 && it.due == td }.sumOf { it.durationMin }
        val free = max(0, prefs.dayEnd * 60 - nowMin - planned)
        val inbox = tasks.count { it.parentId == null && it.status == 0 && it.due == null }
        if (free >= 45 && inbox > 0) {
            out.add(
                Rec(
                    "free:$td",
                    "You have about ${fmtMin(free)} free today",
                    "There are $inbox unscheduled tasks in your inbox. Pick one and give it a time.",
                    null
                )
            )
        }
        return out.filter { it.id !in prefs.dismissed }
    }

    fun dismissRec(id: String) {
        val td = todayStr()
        updatePrefs(prefs.copy(dismissed = prefs.dismissed.filter { it.endsWith(td) }.toSet() + id))
    }

    // ---------- backup / data ----------

    fun exportJson(): String {
        val o = JSONObject()
        o.put("app", "DayFlow")
        o.put("version", 1)
        o.put("tasks", JSONArray(tasks.map { taskToJson(it) }))
        o.put("habits", JSONArray(habits.map { habitToJson(it) }))
        o.put("sessions", JSONArray(sessions.map { sessionToJson(it) }))
        o.put("prefs", prefsToJson(prefs))
        return o.toString(2)
    }

    fun importBackup(text: String): Boolean {
        try {
            val o = JSONObject(text)
            if (o.optString("app") != "DayFlow") return false
            val t = readList(o.getJSONArray("tasks").toString()) { taskFromJson(it) }
            val h = readList(o.getJSONArray("habits").toString()) { habitFromJson(it) }
            val s = readList(o.getJSONArray("sessions").toString()) { sessionFromJson(it) }
            val p = prefsFromJson(o.getJSONObject("prefs"))
            saveTasks(t)
            saveHabits(h)
            saveSessions(s)
            updatePrefs(p)
            appCtx?.let { c -> t.forEach { Reminders.schedule(c, it) } }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun clearAll() {
        appCtx?.let { c -> tasks.forEach { Reminders.cancel(c, it.id) } }
        saveTasks(emptyList())
        saveHabits(emptyList())
        saveSessions(emptyList())
    }

    fun loadSample() {
        val td = LocalDate.now()
        var o = nextOrder()
        val list = mutableListOf<Task>()
        fun mk(
            title: String,
            parent: String? = null,
            due: LocalDate? = null,
            time: String? = null,
            dur: Int = 0,
            pr: Int = 1,
            cat: String = "",
            status: Int = 0
        ): Task {
            o += 1
            return Task(
                id = newId(), parentId = parent, title = title, priority = pr,
                due = due?.toString(), time = time, durationMin = dur, category = cat,
                status = status, doneOn = if (status == 1) td.toString() else null, order = o
            )
        }
        val java = mk("Become job-ready: learn Java", due = td.plusDays(14), pr = 2, cat = "Study")
        list.add(java)
        val oop = mk("Learn OOP", parent = java.id, due = td.plusDays(4), dur = 120, pr = 2, cat = "Study")
        list.add(oop)
        listOf("Classes and objects", "Constructors", "Encapsulation", "Inheritance", "Polymorphism")
            .forEachIndexed { i, s ->
                list.add(mk(s, parent = oop.id, due = td.plusDays(i.toLong()), dur = 30, cat = "Study", status = if (i < 2) 1 else 0))
            }
        list.add(mk("Practice DSA", due = td, time = "10:00", dur = 90, pr = 2, cat = "Study"))
        list.add(mk("Gym session", due = td, time = "18:00", dur = 60, cat = "Health"))
        list.add(mk("Reply to emails", due = td, time = "13:30", dur = 20, cat = "Work"))
        list.add(mk("Submit college assignment", due = td.plusDays(1), pr = 3, cat = "Study"))
        list.add(mk("Buy a notebook"))
        list.add(mk("Plan the weekend"))
        saveTasks(tasks + list)
        appCtx?.let { c -> list.forEach { Reminders.schedule(c, it) } }

        val water = Habit(id = newId(), name = "Drink water", icon = "💧", type = 1, target = 8, unit = "glasses",
            entries = mapOf(td.minusDays(1).toString() to 8, td.minusDays(2).toString() to 9, td.minusDays(3).toString() to 8),
            startDate = td.minusDays(14).toString())
        val run = Habit(id = newId(), name = "Exercise", icon = "🏃", type = 0, days = listOf(1, 3, 5, 6),
            entries = mapOf(td.minusDays(1).toString() to 1, td.minusDays(3).toString() to 1, td.minusDays(5).toString() to 1),
            startDate = td.minusDays(14).toString())
        val read = Habit(id = newId(), name = "Read 10 pages", icon = "📚", type = 1, target = 10, unit = "pages",
            entries = mapOf(td.minusDays(1).toString() to 10, td.minusDays(2).toString() to 12),
            startDate = td.minusDays(14).toString())
        saveHabits(habits + listOf(water, run, read))
    }
}
