package com.hiosdra.hreader.adapter.preferences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceBackupRulesTest {
    @Test
    fun `legacy backup rules exclude secret files`() {
        val rules = readRules("backup_rules.xml")

        assertTrue(rules.contains(SHARED_PREFERENCES_SECRET_EXCLUSION))
        assertTrue(rules.contains(DATASTORE_SECRET_EXCLUSION))
    }

    @Test
    fun `cloud and device transfer rules exclude secret files`() {
        val rules = readRules("data_extraction_rules.xml")

        assertEquals(2, rules.countOccurrences(SHARED_PREFERENCES_SECRET_EXCLUSION))
        assertEquals(2, rules.countOccurrences(DATASTORE_SECRET_EXCLUSION))
    }

    private fun readRules(fileName: String): String = listOf(
        File("app/src/main/res/xml/$fileName"),
        File("src/main/res/xml/$fileName")
    ).firstOrNull(File::isFile)?.readText()
        ?: error("Resource rule not found: $fileName")

    private fun String.countOccurrences(value: String): Int = windowed(value.length)
        .count { it == value }

    private companion object {
        const val SHARED_PREFERENCES_SECRET_EXCLUSION =
            "<exclude domain=\"sharedpref\" path=\"hreader_secrets.xml\" />"
        const val DATASTORE_SECRET_EXCLUSION =
            "<exclude domain=\"file\" path=\"datastore/hreader_secrets.preferences_pb\" />"
    }
}
