package com.backlog.app

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.backlog.db.Spike_note
import com.backlog.greeting

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BackLogApp() }
    }
}

/** Spike screen for task 0.2: a text field and a live list. Task 4.1 replaces it. */
@Composable
fun BackLogApp(viewModel: SpikeViewModel = viewModel(factory = SpikeViewModel.Factory)) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    MaterialTheme {
        Scaffold { padding ->
            SpikeScreen(notes, viewModel::add, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SpikeScreen(notes: List<Spike_note>, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (draft.isNotBlank()) onAdd(draft)
        draft = ""
    }
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("New note") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = submit, enabled = draft.isNotBlank()) { Text("Add") }
        }
        Text("${notes.size} notes · ${greeting()}", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(notes, key = { it.id }) { note ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(note.text, Modifier.weight(1f))
                    Text(timeFormat.format(note.created_at), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
