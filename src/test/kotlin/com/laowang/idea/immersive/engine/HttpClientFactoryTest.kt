package com.laowang.idea.immersive.engine

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HttpClientFactoryTest {
    @Test
    fun `create attaches configured proxy selector`() {
        val proxySelector = RecordingProxySelector()

        val client = HttpClientFactory.create(proxySelector)

        assertSame(proxySelector, client.proxySelector)
    }

    private class RecordingProxySelector : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }
}
