package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDnsConfiguratorTest {
    @Test
    fun rendersIpv4AndIpv6NameserversWithNewlines() {
        val result = AndroidDnsConfigurator.renderResolvConf(listOf("1.1.1.1", "2606:4700:4700::1111"))
        assertTrue(result.contains("nameserver 1.1.1.1\n"))
        assertTrue(result.contains("nameserver 2606:4700:4700::1111\n"))
        assertTrue(result.endsWith("options timeout:2 attempts:2\n"))
    }

    @Test
    fun removesDuplicateNameservers() {
        val result = AndroidDnsConfigurator.renderResolvConf(listOf("8.8.8.8", "8.8.8.8"))
        assertEquals(1, result.split("nameserver 8.8.8.8").size - 1)
    }

    @Test
    fun rejectsInvalidAndEmptyNameserverLists() {
        assertTrue(runCatching { AndroidDnsConfigurator.renderResolvConf(emptyList()) }.isFailure)
        assertTrue(runCatching { AndroidDnsConfigurator.renderResolvConf(listOf("1.1.1.999")) }.isFailure)
        assertTrue(runCatching { AndroidDnsConfigurator.renderResolvConf(listOf("evil.example")) }.isFailure)
    }
}
