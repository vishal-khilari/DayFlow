package com.dayflow.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
            NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun pendingFor(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun cancel(ctx: Context, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendingFor(ctx, id))
    }

    fun scheduleAt(ctx: Context, id: String, millis: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingFor(ctx, id))
    }

    fun schedule(ctx: Context, t: Task) {
        cancel(ctx, t.id)
        if (!t.remind || t.status != 0) return
        val due = parseDate(t.due) ?: return
        val time = try {
            LocalTime.parse(t.time ?: "09:00")
        } catch (e: Exception) {
            LocalTime.of(9, 0)
        }
        val millis = LocalDateTime.of(due, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        scheduleAt(ctx, t.id, millis)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        Store.init(context)
        val nmc = NotificationManagerCompat.from(context)
        if (intent.action == Reminders.ACTION_DONE) {
            Store.markDone(id)
            nmc.cancel(id.hashCode())
            return
        }
        if (intent.action == Reminders.ACTION_SNOOZE) {
            nmc.cancel(id.hashCode())
            val ms = System.currentTimeMillis() + Store.prefs.snoozeMin * 60_000L
            Reminders.scheduleAt(context, id, ms)
            return
        }
        val task = Store.tasks.firstOrNull { it.id == id } ?: return
        if (task.status != 0) return
        Reminders.createChannel(context)

        val open = PendingIntent.getActivity(
            context, id.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val done = PendingIntent.getBroadcast(
            context, id.hashCode() + 1,
            Intent(context, ReminderReceiver::class.java).setAction(Reminders.ACTION_DONE).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snooze = PendingIntent.getBroadcast(
            context, id.hashCode() + 2,
            Intent(context, ReminderReceiver::class.java).setAction(Reminders.ACTION_SNOOZE).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(task.title)
            .setContentText("Due " + (task.time ?: "today"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .addAction(0, "Mark done", done)
            .addAction(0, "Snooze", snooze)
            .build()
        try {
            nmc.notify(id.hashCode(), n)
        } catch (e: SecurityException) {
            // notification permission not granted
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.createChannel(context)
        for (t in Store.tasks) Reminders.schedule(context, t)
    }
}
