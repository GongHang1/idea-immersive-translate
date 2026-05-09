package com.laowang.idea.immersive.trigger

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ImmersiveTranslateActionTest : BasePlatformTestCase() {
    fun testActionIsEnabledWhenEditorAndProjectArePresent() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val action = ImmersiveTranslateAction()
        val event = TestActionEvent.createTestEvent(action) { dataId ->
            when (dataId) {
                CommonDataKeys.EDITOR.name -> myFixture.editor
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testActionIsDisabledWhenEditorIsMissing() {
        val action = ImmersiveTranslateAction()
        val event = TestActionEvent.createTestEvent(action) { dataId ->
            when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        action.update(event)

        assertFalse(event.presentation.isEnabled)
    }
}
