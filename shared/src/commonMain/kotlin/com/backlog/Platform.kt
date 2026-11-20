package com.backlog

/** Name of the platform the shared core is running on. Placeholder until 0.2/1.1. */
expect fun platformName(): String

fun greeting(): String = "BackLog on ${platformName()}"
