package com.hiosdra.hreader.adapter.persistence

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemoteResourcePolicyTest {
    @Test
    fun `normalizes a bare configured server host`() {
        assertEquals("reader.example", normalizedConfiguredHost("reader.example"))
    }

    @Test
    fun `allows configured private server`() {
        val policy = policyFor("10.0.0.4", allowedHosts = setOf("reader.local"))

        assertTrue(policy.allows("http://reader.local/article"))
        assertEquals("10.0.0.4", policy.dns().lookup("reader.local").single().hostAddress)
    }

    @Test
    fun `blocks private address not configured as server`() {
        val policy = policyFor("10.0.0.4")

        assertFalse(policy.allows("http://reader.local/article"))
        try {
            policy.dns().lookup("reader.local")
            fail("Expected private remote resource host to be blocked")
        } catch (_: UnknownHostException) {
        }
    }

    @Test
    fun `blocks non-http schemes`() {
        val policy = policyFor("93.184.216.34")

        assertFalse(policy.allows("file:///etc/passwd"))
        assertFalse(policy.allows("javascript:alert(1)"))
    }

    private fun policyFor(address: String, allowedHosts: Set<String> = emptySet()) =
        RemoteResourcePolicy(
            allowedHosts = { allowedHosts },
            resolveHost = { listOf(InetAddress.getByName(address)) }
        )
}
