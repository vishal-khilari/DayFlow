@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun FocusScreen(initialTask: Task?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember { mutableStateOf(initialTask) }
    var menu by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) } // 0 focus, 1 short break, 2 long break
    var running by remember { mutableStateOf(false) }
    var cycles by remember { mutableIntStateOf(0) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    fun durationFor(m: Int): Long {
        val mins = when (m) {
            0 -> Store.prefs.focusMin
            1 -> Store.prefs.breakMin
            else -> Store.prefs.longBreakMin
        }
        return mins * 60_000L
    }

    var remainingMs by remember { mutableLongStateOf(durationFor(0)) }

    fun saveSession() {
        val mins = max(1, (elapsedMs / 60_000L).toInt())
        val t = selected
        Store.addSession(FocusSession(todayStr(), mins, t?.id, t?.title ?: "Free focus"))
    }

    fun complete() {
        running = false
        if (mode == 0) {
            saveSession()
            cycles += 1
            elapsedMs = 0L
            mode = if (cycles % 4 == 0) 2 else 1
        } else {
            mode = 0
        }
        remainingMs = durationFor(mode)
        try {
            val vib = ctx.getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // vibration not available
        }
    }

    fun stop() {
        running = false
        if (mode == 0 && elapsedMs >= 60_000L) saveSession()
        elapsedMs = 0L
        remainingMs = durationFor(mode)
    }

    LaunchedEffect(running, mode) {
        if (running) {
            var last = System.currentTimeMillis()
            while (running && remainingMs > 0L) {
                delay(250)
                val t = System.currentTimeMillis()
                val dt = t - last
                last = t
                remainingMs -= dt
                if (mode == 0) elapsedMs += dt
            }
            if (running && remainingMs <= 0L) complete()
        }
    }

    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val total = durationFor(mode)
    val secs = max(0L, remainingMs / 1000L)
    val clock = "%02d:%02d".format(secs / 60, secs % 60)
    val modeNames = listOf("Focus", "Short break", "Long break")
    val open = Store.tasks.filter { it.status == 0 }
    val sel = selected
    val tracked = if (sel == null) 0 else Store.actualMinutes(sel.id)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (running) stop()
                onBack()
            }) { Text("‹ Back") }
        }
        Text("Focus", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { menu = true }) { Text(sel?.title ?: "No task (free focus)", maxLines = 1) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("No task (free focus)") }, onClick = { selected = null; menu = false })
                for (t in open.take(30)) {
                    DropdownMenuItem(text = { Text(t.title, maxLines = 1) }, onClick = { selected = t; menu = false })
                }
            }
        }
        if (sel != null) {
            Text(
                "Tracked so far: ${fmtMin(tracked)}" + (if (sel.durationMin > 0) " of ${fmtMin(sel.durationMin)} estimated" else ""),
                fontSize = 12.sp,
                color = muted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) {
                FilterChip(
                    selected = mode == i,
                    onClick = {
                        if (!running) {
                            mode = i
                            elapsedMs = 0L
                            remainingMs = durationFor(i)
                        }
                    },
                    label = { Text(modeNames[i]) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ProgressRing(
            progress = if (total == 0L) 0f else 1f - (remainingMs.toFloat() / total.toFloat()),
            dia = 240.dp,
            label = clock
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "Pause" else if (remainingMs < total) "Resume" else "Start") }
            OutlinedButton(onClick = { stop() }) { Text("Stop") }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Sessions today: ${Store.sessions.count { it.date == todayStr() }} · Focus time: ${fmtMin(Store.focusMinutes(todayStr()))}",
            fontSize = 13.sp,
            color = muted
        )
        Text(
            "Keep this screen open while the timer runs. Lengths can be changed in Settings.",
            fontSize = 11.sp,
            color = muted,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
        )
    }
}
