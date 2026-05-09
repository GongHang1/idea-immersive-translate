package com.laowang.idea.immersive.core

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

object ErrorHandler {
    private const val GROUP_ID = "ImmersiveTranslate"
    private const val SETTINGS_DISPLAY_NAME = "Immersive Translate"

    fun notify(project: Project?, error: TranslationError) {
        val (message, type, action) = when (error) {
            TranslationError.NoApiKey -> Triple(
                "API key not configured. Click to open Settings.",
                NotificationType.WARNING,
                openSettingsAction(),
            )
            TranslationError.NetworkTimeout -> Triple(
                "Network timeout. Check IDE proxy, provider base URL, or retry.",
                NotificationType.WARNING,
                null,
            )
            is TranslationError.RateLimited -> Triple(
                "Rate limited by upstream. Retry after ${error.retryAfterMs}ms.",
                NotificationType.WARNING,
                null,
            )
            is TranslationError.ApiError -> Triple(
                "Translation API error ${error.code}: ${error.message}",
                NotificationType.ERROR,
                null,
            )
            is TranslationError.Unknown -> {
                val detail = error.cause.message?.takeIf { it.isNotBlank() }
                    ?: error.cause::class.java.simpleName
                Triple("Unknown translation error: $detail", NotificationType.ERROR, null)
            }
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Immersive Translate", message, type)
        action?.let(notification::addAction)
        notification.notify(project)
    }

    private fun openSettingsAction(): AnAction =
        object : AnAction("Open Settings") {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SETTINGS_DISPLAY_NAME)
            }
        }
}
