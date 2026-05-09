@file:Suppress("DEPRECATION")

package com.laowang.idea.immersive.engine

import com.intellij.util.net.HttpConfigurable
import java.net.ProxySelector
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(
        proxySelector: ProxySelector? = ideProxySelector(),
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30.seconds.toJavaDuration())
            .readTimeout(60.seconds.toJavaDuration())
            .writeTimeout(30.seconds.toJavaDuration())
            .callTimeout(90.seconds.toJavaDuration())

        proxySelector?.let(builder::proxySelector)
        return builder.build()
    }

    private fun ideProxySelector(): ProxySelector? =
        runCatching { HttpConfigurable.getInstance().getOnlyBySettingsSelector() }.getOrNull()
}
