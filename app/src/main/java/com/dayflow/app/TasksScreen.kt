@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FilterNames = listOf("Inbox", "Today", "Upcoming", "All", "Done")
private val SortNames = listOf("Manual", "Due date", "Priority", "A–Z")

fun sortTasks(list: List<Task>, sort: Int): List<Task> = when (sort) {
    1 -> list.sortedWith(compareBy<Task>({ it.due ?: "9999-99-99" }, { it.time ?: "99:99" }))
    2 -> list.sortedWith(compareBy<Task>({ -it.priority }, { it.due ?: "9999-99-99" }))
    3 -> list.sortedBy { it.title.lowercase() }
    else -> list.sortedBy { it.order }
}

private fun matchesFilter(t: Task, f: Int): Boolean {
    val td = todayStr()
    return when (f) {
        0 -> t.status == 0 && t.due == null
        1 -> Store.subtreeAny(t) { x ->
            val d = x.due
            x.status == 0 && d != null && d <= td
        }
        2 -> Store.subtreeAny(t) { x ->
            val d = x.due
            x.status == 0 && d != null && d > td
        }
        3 -> t.status != 2
        else -> t.status == 1
    }
}

private fun addRows(t: Task, depth: Int, expanded: Map<String, Boolean>, sort: Int, out: MutableList<Pair<Task, Int>>) {
    out.add(Pair(t, depth))
    if (expanded[t.id] == true) {
        for (c in sortTasks(Store.childrenOf(t.id), sort)) addRows(c, depth + 1, expanded, sort, out)
    }
}

@Composable
fun TasksScreen(actions: TaskActions) {
    var filter by remember { mutableIntStateOf(1) }
    var sort by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val tasks = Store.tasks
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val rows = ArrayList<Pair<Task, Int>>()
    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        for (t in tasks) {
            if (t.title.lowercase().contains(q) || t.notes.lowercase().contains(q) || t.category.lowercase().contains(q)) {
                rows.add(Pair(t, 0))
            }
        }
    } else {
        val roots = sortTasks(tasks.filter { it.parentId == null && matchesFilter(it, filter) }, sort)
        for (r in roots) addRows(r, 0, expanded, sort, rows)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Tasks", "${tasks.count { it.status == 0 }} open")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tasks, notes, categories") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterNames.forEachIndexed { i, name ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(name) })
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort:", fontSize = 12.sp, color = muted, modifier = Modifier.padding(start = 4.dp))
            SortNames.forEachIndexed { i, name ->
                TextButton(onClick = { sort = i }) {
                    Text(name, fontSize = 12.sp, fontWeight = if (sort == i) FontWeight.Bold else FontWeight.Normal)
                }
            }
            TextButton(onClick = { for (t in tasks) expanded[t.id] = true }) { Text("Expand all", fontSize = 12.sp) }
            TextButton(onClick = { expanded.clear() }) { Text("Collapse all", fontSize = 12.sp) }
        }
        if (rows.isEmpty()) {
            EmptyState(
                if (query.isNotBlank()) "🔍" else "🌤️",
                if (query.isNotBlank()) "No tasks match your search." else "Nothing here. Tap + to add a task."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.first.id }) { row ->
                    val t = row.first
                    TaskRow(
                        task = t,
                        depth = row.second,
                        expanded = expanded[t.id] == true,
                        actions = actions,
                        onExpand = { expanded[t.id] = !(expanded[t.id] ?: false) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRow(task: Task, depth: Int, expanded: Boolean, actions: TaskActions, onExpand: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val kids = Store.childrenOf(task.id)
    var menu by remember { mutableStateOf(false) }
    val progress = Store.subProgress(task.id)
    val doneN = progress.first
    val totalN = progress.second
    val done = task.status == 1

    DfCard(modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clickable(enabled = kids.isNotEmpty()) { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (kids.isNotEmpty()) Text(if (expanded) "▾" else "▸", fontSize = 16.sp, color = muted)
            }
            CheckCircle(done, priorityColor(task.priority)) { actions.toggle(task) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).clickable { actions.edit(task) }) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done || task.status == 2) muted else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (totalN > 0) {
                    Text(
                        "$doneN of $totalN subtasks completed",
                        fontSize = 12.sp,
                        color = if (doneN == totalN) GreenC else muted
                    )
                }
                if (task.status == 2) Text("Skipped", fontSize = 12.sp, color = muted)
                MetaRow(task)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; actions.edit(task) })
                    DropdownMenuItem(text = { Text("Add subtask") }, onClick = {
                        menu = false
                        actions.addSub(task)
                    })
                    DropdownMenuItem(text = { Text("Start focus timer") }, onClick = { menu = false; actions.focus(task) })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; Store.move(task.id, -1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; Store.move(task.id, 1) })
                    if (task.parentId != null) {
                        DropdownMenuItem(text = { Text("Make top-level task") }, onClick = { menu = false; Store.promote(task.id) })
                    }
                    DropdownMenuItem(
                        text = { Text(if (task.status == 2) "Un-skip" else "Skip") },
                        onClick = { menu = false; Store.setStatus(task.id, if (task.status == 2) 0 else 2) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = RedC) },
                        onClick = { menu = false; actions.delete(task) }
                    )
                }
            }
        }
        if (totalN > 0) {
            LinearProgressIndicator(
                progress = { doneN.toFloat() / totalN },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).height(3.dp)
            )
        }
    }
}
