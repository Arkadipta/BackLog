package com.backlog

actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"
