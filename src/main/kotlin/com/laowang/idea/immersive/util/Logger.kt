package com.laowang.idea.immersive.util

import com.intellij.openapi.diagnostic.Logger

object Log {
    private val logger = Logger.getInstance(Log::class.java)

    fun debug(message: String) {
        logger.debug(message)
    }

    fun info(message: String) {
        logger.info(message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            logger.warn(message)
            return
        }
        logger.warn(message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            logger.error(message)
            return
        }
        logger.error(message, throwable)
    }
}
