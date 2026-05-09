package com.laowang.idea.immersive.engine

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HttpClientFactoryTest {
    @Test
    fun `create attaches configured proxy selector`() {
        val proxySelector = RecordingProxySelector()

        val client = HttpClientFactory.create(proxySelector)

        assertSame(proxySelector, client.proxySelector)
    }

    @Test
    fun `system aware selector falls back to system proxy when ide settings are direct`() {
        val systemProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1082))
        val selector = HttpClientFactory.systemAwareProxySelector(
            settingsSelector = FixedProxySelector(listOf(Proxy.NO_PROXY)),
            fallbackSelector = FixedProxySelector(listOf(systemProxy)),
        )

        assertEquals(listOf(systemProxy), selector?.select(URI("https://translate.googleapis.com")))
    }

    @Test
    fun `system aware selector prefers explicit ide proxy settings`() {
        val ideProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8080))
        val systemProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1082))
        val selector = HttpClientFactory.systemAwareProxySelector(
            settingsSelector = FixedProxySelector(listOf(ideProxy)),
            fallbackSelector = FixedProxySelector(listOf(systemProxy)),
        )

        assertEquals(listOf(ideProxy), selector?.select(URI("https://translate.googleapis.com")))
    }

    private class FixedProxySelector(private val proxies: List<Proxy>) : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = proxies

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
    }

    private class RecordingProxySelector : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }
}
