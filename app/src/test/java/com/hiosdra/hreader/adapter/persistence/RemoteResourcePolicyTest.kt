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
    fun `normalizes a configured host with a case insensitive scheme`() {
        assertEquals("reader.example", normalizedConfiguredHost("HTTPS://READER.EXAMPLE"))
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

    @Test
    fun `blocks URLs with embedded credentials`() {
        val policy = policyFor("93.184.216.34")

        assertFalse(policy.allows("https://user:password@example.com/article"))
    }

    @Test
    fun `blocks private IPv4 addresses mapped into IPv6`() {
        val policy = policyFor("::ffff:10.0.0.4")

        assertFalse(policy.allows("https://public.example/article"))
    }

    @Test
    fun `blocks carrier grade NAT addresses`() {
        val policy = policyFor("100.64.0.1")

        assertFalse(policy.allows("https://carrier.example/article"))
    }

    @Test
    fun `blocks documentation addresses`() {
        val policy = policyFor("192.0.2.1")

        assertFalse(policy.allows("https://documentation.example/article"))
    }

    @Test
    fun `allows public IPv4 addresses mapped into IPv6`() {
        val policy = policyFor("::ffff:93.184.216.34")

        assertTrue(policy.allows("https://public.example/article"))
    }

    @Test
    fun `requires every resolved address to be public`() {
        val policy = RemoteResourcePolicyAdapter(
            allowedHosts = { emptySet() },
            resolveHost = {
                listOf(
                    InetAddress.getByName("93.184.216.34"),
                    InetAddress.getByName("10.0.0.4")
                )
            }
        )

        assertFalse(policy.allows("https://mixed.example/article"))
    }

    @Test
    fun `rechecks DNS results for every request`() {
        var address = "93.184.216.34"
        val policy = RemoteResourcePolicyAdapter(
            allowedHosts = { emptySet() },
            resolveHost = { listOf(InetAddress.getByName(address)) }
        )

        assertTrue(policy.allows("https://rebinding.example/article"))
        address = "10.0.0.4"

        assertFalse(policy.allows("https://rebinding.example/article"))
        try {
            policy.dns().lookup("rebinding.example")
            fail("Expected rebinding target to be blocked")
        } catch (_: UnknownHostException) {
        }
    }

    private fun policyFor(address: String, allowedHosts: Set<String> = emptySet()) =
        RemoteResourcePolicyAdapter(
            allowedHosts = { allowedHosts },
            resolveHost = { listOf(InetAddress.getByName(address)) }
        )
}
