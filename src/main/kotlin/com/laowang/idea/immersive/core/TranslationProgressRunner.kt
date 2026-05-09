package com.laowang.idea.immersive.core

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

fun interface TranslationProgressRunner {
    fun run(project: Project, title: String, task: (TranslationCancellationToken) -> Unit)
}

object IdeTranslationProgressRunner : TranslationProgressRunner {
    override fun run(project: Project, title: String, task: (TranslationCancellationToken) -> Unit) {
        val cancellationToken = TranslationCancellationToken()
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = title
                val finished = AtomicBoolean(false)
                val monitor = monitorCancellation(indicator, cancellationToken, finished)
                try {
                    task(cancellationToken)
                } finally {
                    finished.set(true)
                    monitor.interrupt()
                }
            }

            override fun onCancel() {
                cancellationToken.cancel()
            }
        }.queue()
    }

    private fun monitorCancellation(
        indicator: ProgressIndicator,
        cancellationToken: TranslationCancellationToken,
        finished: AtomicBoolean,
    ): Thread =
        thread(name = "Immersive Translate Cancellation Monitor", isDaemon = true) {
            while (!finished.get() && !cancellationToken.isCancelled) {
                if (indicator.isCanceled) {
                    cancellationToken.cancel()
                    return@thread
                }
                runCatching { Thread.sleep(100) }
            }
        }
}
