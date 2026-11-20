package com.backlog.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.backlog.db.Spike_note
import com.backlog.spike.SpikeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Spike ViewModel for task 0.2: wraps the shared repository's Flow. Task 4.1 replaces it. */
class SpikeViewModel(private val repository: SpikeRepository) : ViewModel() {
    val notes: StateFlow<List<Spike_note>> = repository.notes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) {
        viewModelScope.launch { repository.add(text) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SpikeViewModel((this[APPLICATION_KEY] as BackLogApplication).spikeRepository) }
        }
    }
}
