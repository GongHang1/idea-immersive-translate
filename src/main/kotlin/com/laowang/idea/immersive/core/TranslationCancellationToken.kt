package com.laowang.idea.immersive.core

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TranslationCancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            callbacks.forEach { callback -> runCatching { callback() } }
        }
    }

    fun throwIfCancelled() {
        if (isCancelled) {
            throw TranslationCancelledException()
        }
    }

    fun onCancel(callback: () -> Unit): AutoCloseable {
        if (isCancelled) {
            callback()
            return AutoCloseable {}
        }
        callbacks += callback
        if (isCancelled && callbacks.remove(callback)) {
            callback()
        }
        return AutoCloseable { callbacks.remove(callback) }
    }
}

class TranslationCancelledException(cause: Throwable? = null) : RuntimeException("Translation cancelled", cause)
