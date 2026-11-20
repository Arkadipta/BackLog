package com.backlog.app

import android.app.Application
import com.backlog.spike.SpikeRepository
import com.backlog.spike.createSpikeRepository

/** Owns the process-wide database. Task 4.1 replaces the spike repository. */
class BackLogApplication : Application() {
    val spikeRepository: SpikeRepository by lazy { createSpikeRepository(this) }
}
