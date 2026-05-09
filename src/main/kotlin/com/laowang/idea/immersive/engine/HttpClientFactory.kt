@file:Suppress("DEPRECATION")

package com.laowang.idea.immersive.engine

import com.intellij.util.net.HttpConfigurable
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(
        proxySelector: ProxySelector? = systemAwareProxySelector(),
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30.seconds.toJavaDuration())
            .readTimeout(60.seconds.toJavaDuration())
            .writeTimeout(30.seconds.toJavaDuration())
            .callTimeout(90.seconds.toJavaDuration())

        proxySelector?.let(builder::proxySelector)
        return builder.build()
    }

    internal fun systemAwareProxySelector(
        settingsSelector: ProxySelector? = ideProxySelector(),
        fallbackSelector: ProxySelector? = ProxySelector.getDefault(),
    ): ProxySelector? {
        if (settingsSelector == null) {
            return fallbackSelector
        }
        if (fallbackSelector == null || fallbackSelector === settingsSelector) {
            return settingsSelector
        }
        return SettingsThenSystemProxySelector(settingsSelector, fallbackSelector)
    }

    private fun ideProxySelector(): ProxySelector? =
        runCatching { HttpConfigurable.getInstance().getOnlyBySettingsSelector() }.getOrNull()

    private class SettingsThenSystemProxySelector(
        private val settingsSelector: ProxySelector,
        private val fallbackSelector: ProxySelector,
    ) : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val settingsProxies = runCatching { settingsSelector.select(uri) }.getOrDefault(emptyList())
            if (settingsProxies.hasRealProxy()) {
                return settingsProxies
            }
            return fallbackSelector.select(uri)
        }

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
            runCatching { settingsSelector.connectFailed(uri, sa, ioe) }
            fallbackSelector.connectFailed(uri, sa, ioe)
        }
    }

    private fun List<Proxy>.hasRealProxy(): Boolean =
        any { it.type() != Proxy.Type.DIRECT }
}
