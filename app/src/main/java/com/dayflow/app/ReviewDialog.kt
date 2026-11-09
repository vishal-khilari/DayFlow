@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

val Moods = listOf("😞", "🙁", "😐", "🙂", "😄")

@Composable
fun ReviewDialog(onClose: () -> Unit) {
    val td = todayStr()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val doneToday = Store.tasks.count { it.status == 1 && it.doneOn == td }
    val unfinished = Store.tasks.count { val d = it.due; it.status == 0 && d != null && d <= td }
    var mood by remember { mutableIntStateOf(3) }
    var wins by remember { mutableStateOf("") }
    var blockers by remember { mutableStateOf("") }
    var tomorrow by remember { mutableStateOf("") }
    var moveOn by remember { mutableStateOf(unfinished > 0) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Evening review") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Today: $doneToday done, $unfinished still open.", fontSize = 13.sp, color = muted)
                Text("How was your day?", fontSize = 12.sp, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Moods.forEachIndexed { i, m ->
                        FilterChip(selected = mood == i, onClick = { mood = i }, label = { Text(m, fontSize = 18.sp) })
                    }
                }
                OutlinedTextField(
                    value = wins, onValueChange = { wins = it },
                    label = { Text("What went well?") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = blockers, onValueChange = { blockers = it },
                    label = { Text("What got in the way?") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tomorrow, onValueChange = { tomorrow = it },
                    label = { Text("Top priority for tomorrow") }, modifier = Modifier.fillMaxWidth()
                )
                if (unfinished > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("Move $unfinished unfinished task(s) to tomorrow", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = moveOn, onCheckedChange = { moveOn = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.addReview(Review(td, mood, wins.trim(), blockers.trim(), tomorrow.trim()))
                if (moveOn && unfinished > 0) Store.moveUnfinishedToTomorrow()
                if (tomorrow.isNotBlank()) {
                    Store.addTask(
                        Task(
                            id = Store.newId(), title = tomorrow.trim(), priority = 2,
                            due = LocalDate.now().plusDays(1).toString(), order = Store.nextOrder()
                        )
                    )
                }
                onClose()
            }) { Text("Save review") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Not now") } }
    )
}
