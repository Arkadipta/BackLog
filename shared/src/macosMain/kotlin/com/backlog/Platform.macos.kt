package com.backlog

import platform.Foundation.NSProcessInfo

actual fun platformName(): String = "macOS ${NSProcessInfo.processInfo.operatingSystemVersionString}"
