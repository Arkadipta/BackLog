package com.backlog.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.backlog.diagnostics.DatabaseStatus
import com.backlog.diagnostics.loadDatabaseStatus
import com.backlog.greeting

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BackLogApp() }
    }
}

/**
 * Placeholder until task 4.1: opens the real database (creating, migrating
 * and seeding it) and shows what it found, including D9's full-text check.
 */
@Composable
fun BackLogApp() {
    val context = LocalContext.current
    val status by produceState<DatabaseStatus?>(null) {
        value = loadDatabaseStatus(context).also { Log.i("BackLog", "database status: $it") }
    }
    MaterialTheme {
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(greeting(), style = MaterialTheme.typography.titleMedium)
                val s = status
                if (s == null) {
                    Text("Opening database…")
                } else {
                    StatusLines(s)
                }
            }
        }
    }
}

@Composable
private fun StatusLines(s: DatabaseStatus) {
    Text("SQLite ${s.sqliteVersion} · FTS4 ${yesNo(s.fts4)} · FTS5 ${yesNo(s.fts5)}")
    Text("Schema version ${s.schemaVersion}")
    Text("Lists: ${s.lists.joinToString { it.name + if (it.isInbox) " (inbox)" else "" }}")
    Text("Device ${s.deviceId}", style = MaterialTheme.typography.labelMedium)
    Text(s.location, style = MaterialTheme.typography.labelMedium)
    s.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"
