package com.dayflow.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "dayflow_reminders"
    const val ACTION_DONE = "com.dayflow.app.ACTION_DONE"
    const val ACTION_SNOOZE = "com.dayflow.app.ACTION_SNOOZE"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun pendingFor(ctx: Context, key: String, kind: String, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("kind", kind)
        i.putExtra("id", id)
        i.putExtra("key", key)
        return PendingIntent.getBroadcast(
            ctx, key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun cancelKey(ctx: Context, key: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendingFor(ctx, key, "x", "x"))
    }

    fun setAlarm(ctx: Context, key: String, kind: String, id: String, millis: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingFor(ctx, key, kind, id)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun millisOf(d: LocalDate, t: LocalTime): Long =
        LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ----- tasks -----
    fun cancel(ctx: Context, id: String) = cancelKey(ctx, "task:$id")

    fun schedule(ctx: Context, t: Task) {
        val key = "task:" + t.id
        cancelKey(ctx, key)
        if (!t.remind || t.status != 0) return
        var millis = t.remindAt
        if (millis <= 0L) {
            val due = parseDate(t.due) ?: return
            millis = millisOf(due, parseTime(t.time))
        }
        if (millis <= System.currentTimeMillis()) return
        setAlarm(ctx, key, "task", t.id, millis)
    }

    // ----- habits -----
    fun cancelHabit(ctx: Context, id: String) = cancelKey(ctx, "habit:$id")

    fun nextHabitTime(h: Habit, after: Long): Long? {
        val rt = h.remindTime ?: return null
        val time = parseTime(rt)
        for (i in 0..8) {
            val d = LocalDate.now().plusDays(i.toLong())
            if (d.dayOfWeek.value !in h.days || d.toString() < h.startDate) continue
            if (d.toString() in h.skipped) continue
            val ms = millisOf(d, time)
            if (ms > after) return ms
        }
        return null
    }

    fun scheduleHabit(ctx: Context, h: Habit) {
        val key = "habit:" + h.id
        cancelKey(ctx, key)
        val ms = nextHabitTime(h, System.currentTimeMillis()) ?: return
        setAlarm(ctx, key, "habit", h.id, ms)
    }

    // ----- daily planning / evening review -----
    private fun nextDaily(hhmm: String): Long {
        val t = parseTime(hhmm)
        var ms = millisOf(LocalDate.now(), t)
        if (ms <= System.currentTimeMillis() + 1000L) ms = millisOf(LocalDate.now().plusDays(1), t)
        return ms
    }

    fun scheduleDaily(ctx: Context) {
        val p = Store.prefs
        cancelKey(ctx, "daily:morning")
        cancelKey(ctx, "daily:evening")
        if (p.morningOn) setAlarm(ctx, "daily:morning", "morning", "morning", nextDaily(p.morningTime))
        if (p.eveningOn) setAlarm(ctx, "daily:evening", "evening", "evening", nextDaily(p.eveningTime))
    }

    fun scheduleAll(ctx: Context) {
        for (t in Store.tasks) schedule(ctx, t)
        for (h in Store.habits) scheduleHabit(ctx, h)
        scheduleDaily(ctx)
    }

    fun sendTest(ctx: Context) {
        createChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 77, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("DayFlow reminders work")
            .setContentText("You will be notified like this at the time you choose.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(7777, n)
        } catch (e: SecurityException) {
            // permission missing
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    private fun broadcast(ctx: Context, code: Int, action: String, kind: String, id: String, key: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = action
        i.putExtra("kind", kind)
        i.putExtra("id", id)
        i.putExtra("key", key)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val id = intent.getStringExtra("id") ?: return
        val key = intent.getStringExtra("key") ?: return
        Store.init(context)
        Reminders.createChannel(context)
        val nmc = NotificationManagerCompat.from(context)
        val nid = key.hashCode()

        if (intent.action == Reminders.ACTION_DONE) {
            if (kind == "task") Store.markDone(id)
            if (kind == "habit") Store.completeHabitToday(id)
            nmc.cancel(nid)
            return
        }
        if (intent.action == Reminders.ACTION_SNOOZE) {
            nmc.cancel(nid)
            Reminders.setAlarm(context, key, kind, id, System.currentTimeMillis() + Store.prefs.snoozeMin * 60_000L)
            return
        }

        var title = ""
        var text = ""
        var openAction: String? = null
        var withButtons = false

        if (kind == "task") {
            val task = Store.tasks.firstOrNull { it.id == id } ?: return
            if (task.status != 0) return
            title = task.title
            val d = task.due
            text = if (d != null) "Due " + dueLabel(d) + (if (task.time != null) " at " + fmtHHmm(task.time) else "") else "Reminder"
            withButtons = true
        } else if (kind == "habit") {
            val h = Store.habits.firstOrNull { it.id == id } ?: return
            Reminders.scheduleHabit(context, h)
            if (h.doneOn(LocalDate.now())) return
            title = h.icon + " " + h.name
            text = if (h.type == 1) "Today's target: ${h.target} ${h.unit}".trim() else "Time for your habit"
            openAction = "habits"
            withButtons = true
        } else if (kind == "morning") {
            Reminders.scheduleDaily(context)
            val n = Store.tasks.count { it.status == 0 && it.due == todayStr() }
            title = "Plan your day"
            text = if (n == 0) "Nothing is scheduled yet. Add what matters today." else "You have $n task" + (if (n == 1) "" else "s") + " today."
            openAction = "calendar"
        } else {
            Reminders.scheduleDaily(context)
            title = "Evening review"
            text = "How did today go? Take a minute to wrap up."
            openAction = "review"
        }

        val openIntent = Intent(context, MainActivity::class.java)
        openIntent.action = "open:$key"
        if (openAction != null) openIntent.putExtra("action", openAction)
        val open = PendingIntent.getActivity(context, nid, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val b = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
        if (withButtons) {
            b.addAction(0, "Mark done", broadcast(context, nid + 1, Reminders.ACTION_DONE, kind, id, key))
            b.addAction(0, "Snooze", broadcast(context, nid + 2, Reminders.ACTION_SNOOZE, kind, id, key))
        }
        try {
            nmc.notify(nid, b.build())
        } catch (e: SecurityException) {
            // notification permission not granted
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.createChannel(context)
        Reminders.scheduleAll(context)
    }
}
