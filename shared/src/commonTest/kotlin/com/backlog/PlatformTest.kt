package com.backlog

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun greetingNamesThePlatform() {
        val text = greeting()
        assertTrue(text.startsWith("BackLog on "), text)
        assertTrue(platformName().isNotBlank())
    }
}
