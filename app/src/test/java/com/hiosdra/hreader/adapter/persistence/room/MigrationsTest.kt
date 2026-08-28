package com.hiosdra.hreader.adapter.persistence.room

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsTest {
    @Test
    fun migrationsFormAContinuousChainToTheCurrentSchema() {
        assertEquals(listOf(15, 16, 17), APP_MIGRATIONS.map { it.startVersion })
        assertEquals(listOf(16, 17, 18), APP_MIGRATIONS.map { it.endVersion })
        APP_MIGRATIONS.asList().zipWithNext().forEach { (current, next) ->
            assertEquals(current.endVersion, next.startVersion)
        }
    }
}
