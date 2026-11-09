@file:OptIn(ExperimentalMaterial3Api::class)

package com.dayflow.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

data class EditorRequest(val task: Task?, val parentId: String?)

@Composable
fun DayFlowApp(launchAction: String?, onLaunchHandled: () -> Unit) {
    var tab by remember { mutableIntStateOf(Store.prefs.startTab.coerceIn(0, 4)) }
    var showReview by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var confirmComplete by remember { mutableStateOf<Task?>(null) }
    var confirmDelete by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(launchAction) {
        if (launchAction == "quickAdd") editor = EditorRequest(null, null)
        if (launchAction == "focus") overlay = "focus"
        if (launchAction == "habits") tab = 3
        if (launchAction == "calendar") tab = 1
        if (launchAction == "review") showReview = true
        if (launchAction != null) onLaunchHandled()
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    val actions = TaskActions(
        toggle = { t ->
            if (t.status == 0 && Store.descendants(t.id).any { it.status == 0 }) {
                confirmComplete = t
            } else {
                Store.toggle(t.id)
            }
        },
        edit = { t -> editor = EditorRequest(t, t.parentId) },
        addSub = { t -> editor = EditorRequest(null, t.id) },
        delete = { t ->
            if (Store.descendants(t.id).isNotEmpty()) {
                confirmDelete = t
            } else {
                Store.deleteTree(t.id)
            }
        },
        focus = { t ->
            focusTask = t
            overlay = "focus"
        }
    )

    val tabs = listOf("Home" to "🏠", "Calendar" to "📅", "Tasks" to "✅", "Habits" to "🔁", "Insights" to "📊")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (overlay == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { i, pair ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(pair.second) },
                            label = { Text(pair.first) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (overlay == null && tab != 3) {
                FloatingActionButton(
                    onClick = { editor = EditorRequest(null, null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Quick add task")
                }
            }
        }
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            when (overlay) {
                "focus" -> FocusScreen(initialTask = focusTask, onBack = { overlay = null })
                "settings" -> SettingsScreen(onBack = { overlay = null })
                else -> Crossfade(targetState = tab, label = "tabs") { t ->
                    when (t) {
                        0 -> HomeScreen(
                            actions = actions,
                            onFocus = {
                                focusTask = null
                                overlay = "focus"
                            },
                            onSettings = { overlay = "settings" },
                            onReview = { showReview = true },
                            onTab = { tab = it }
                        )
                        1 -> AgendaScreen(actions)
                        2 -> TasksScreen(actions)
                        3 -> HabitsScreen()
                        else -> InsightsScreen()
                    }
                }
            }
        }
    }

    val cc = confirmComplete
    if (cc != null) {
        val n = Store.descendants(cc.id).count { it.status == 0 }
        AlertDialog(
            onDismissRequest = { confirmComplete = null },
            title = { Text("Complete everything?") },
            text = { Text("\"" + cc.title + "\" still has " + n + " open subtask" + (if (n == 1) "" else "s") + ". Complete the task and all of its subtasks?") },
            confirmButton = {
                TextButton(onClick = {
                    Store.completeTree(cc.id)
                    confirmComplete = null
                }) { Text("Complete all") }
            },
            dismissButton = { TextButton(onClick = { confirmComplete = null }) { Text("Cancel") } }
        )
    }

    val cd = confirmDelete
    if (cd != null) {
        val n = Store.descendants(cd.id).size
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete task and subtasks?") },
            text = { Text("\"" + cd.title + "\" and its " + n + " nested subtask" + (if (n == 1) "" else "s") + " will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.deleteTree(cd.id)
                    confirmDelete = null
                }) { Text("Delete all", color = RedC) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    if (showReview) {
        ReviewDialog(onClose = { showReview = false })
    }

    val ed = editor
    if (ed != null) {
        TaskEditor(req = ed, onClose = { editor = null })
    }
}
