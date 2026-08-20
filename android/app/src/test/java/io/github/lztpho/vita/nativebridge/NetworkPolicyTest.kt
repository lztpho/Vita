// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class NetworkPolicyTest {
    @Test fun requiresCredentialFreeHttps() {
        assertEquals("https", NetworkPolicy.validate("https://api.example.com/v1").scheme)
        assertThrows(IllegalArgumentException::class.java) { NetworkPolicy.validate("http://api.example.com/v1") }
        assertThrows(IllegalArgumentException::class.java) { NetworkPolicy.validate("https://user:secret@example.com/v1") }
        assertThrows(IllegalArgumentException::class.java) { NetworkPolicy.validate("https://api.example.com/v1?target=other") }
        assertThrows(IllegalArgumentException::class.java) { NetworkPolicy.validate("https://api.example.com/v1#fragment") }
    }

    @Test fun privateAndReservedRangesAreRejected() {
        listOf(
            "0.0.0.1", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.0.2.1", "192.168.1.1", "198.18.0.1",
            "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::1", "fc00::1", "fe80::1", "2001:db8::1", "ff02::1",
        ).forEach { assertFalse(it, NetworkPolicy.isPublic(InetAddress.getByName(it))) }
        assertTrue(NetworkPolicy.isPublic(InetAddress.getByName("8.8.8.8")))
        assertTrue(NetworkPolicy.isPublic(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test fun dnsFailsIfAnyAnswerIsNotPublic() {
        val mixed = object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))
        }
        assertThrows(UnknownHostException::class.java) { NetworkPolicy.publicDns(mixed).lookup("api.example.com") }
    }

    @Test fun requestValidationResolvesDnsEveryTime() {
        var calls = 0
        val public = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                calls += 1
                return listOf(InetAddress.getByName("8.8.8.8"))
            }
        }
        repeat(2) { NetworkPolicy.validateForRequest("https://api.example.com/v1", public) }
        assertEquals(2, calls)
    }
}
