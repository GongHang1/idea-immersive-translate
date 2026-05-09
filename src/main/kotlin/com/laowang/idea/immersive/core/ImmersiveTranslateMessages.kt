package com.laowang.idea.immersive.core

import java.util.ResourceBundle

object ImmersiveTranslateMessages {
    private const val BUNDLE_NAME = "messages.ImmersiveTranslateBundle"

    fun message(key: String, fallback: String): String =
        runCatching { ResourceBundle.getBundle(BUNDLE_NAME).getString(key) }
            .getOrDefault(fallback)
}
