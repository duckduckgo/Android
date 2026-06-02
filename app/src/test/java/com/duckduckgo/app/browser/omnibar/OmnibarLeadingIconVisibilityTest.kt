package com.duckduckgo.app.browser.omnibar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmnibarLeadingIconVisibilityTest {

    @Test
    fun whenNotDuckAiModeThenFireShownAndPlusHidden() {
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = false, isNativeInputEnabled = true))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = false, isNativeInputEnabled = true))
    }

    @Test
    fun whenNotDuckAiModeAndNativeInputDisabledThenFireShownAndPlusHidden() {
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = false, isNativeInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = false, isNativeInputEnabled = false))
    }

    @Test
    fun whenDuckAiModeAndNativeInputEnabledThenPlusShownAndFireHidden() {
        assertTrue(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = true, isNativeInputEnabled = true))
        assertFalse(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = true, isNativeInputEnabled = true))
    }

    @Test
    fun whenDuckAiModeAndNativeInputDisabledThenFireShownAndPlusHidden() {
        // Regression: a user with the nativeInputField flag off must keep the fire button
        // in the omnibar, not the + button, even while in a Duck.ai view.
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = true, isNativeInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = true, isNativeInputEnabled = false))
    }

    @Test
    fun whenFireIconHiddenThenNeitherFireNorPlusShown() {
        assertFalse(shouldShowFireIcon(showFireIcon = false, isDuckAiMode = true, isNativeInputEnabled = true))
        assertFalse(shouldShowPlusIcon(showFireIcon = false, isDuckAiMode = true, isNativeInputEnabled = true))
        assertFalse(shouldShowFireIcon(showFireIcon = false, isDuckAiMode = false, isNativeInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = false, isDuckAiMode = false, isNativeInputEnabled = false))
    }

    @Test
    fun whenFireIconShownThenExactlyOneOfFireOrPlusIsVisible() {
        for (duckAi in listOf(true, false)) {
            for (nativeInput in listOf(true, false)) {
                val fire = shouldShowFireIcon(showFireIcon = true, isDuckAiMode = duckAi, isNativeInputEnabled = nativeInput)
                val plus = shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = duckAi, isNativeInputEnabled = nativeInput)
                assertTrue("exactly one leading icon expected for duckAi=$duckAi nativeInput=$nativeInput", fire xor plus)
            }
        }
    }
}
