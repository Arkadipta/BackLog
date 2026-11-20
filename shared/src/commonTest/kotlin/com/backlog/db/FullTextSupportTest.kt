package com.backlog.db

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D9 on the test targets. The schema uses FTS4, because Android 12's SQLite
 * has no FTS5, so every SQLite it runs on must have FTS4. The jvm and Android
 * host tests run sqlite-jdbc; macosArm64 runs the system SQLite the Mac app
 * links. Android's own SQLite was checked on an API 31 emulator (phase 1 notes).
 */
class FullTextSupportTest {
    @Test
    fun thisSqliteHasFts4() {
        val driver = emptyTestDriver()
        val support = driver.probeFullText()
        println("D9 probe on $testSqliteName ${driver.sqliteVersion()}: $support")
        assertTrue(support.fts4, "no FTS4 in $testSqliteName")
    }

    @Test
    fun probeLeavesNothingBehind() {
        val driver = emptyTestDriver()
        driver.probeFullText()
        driver.probeFullText()
        assertTrue(driver.column("SELECT name FROM sqlite_temp_master").isEmpty())
    }
}
