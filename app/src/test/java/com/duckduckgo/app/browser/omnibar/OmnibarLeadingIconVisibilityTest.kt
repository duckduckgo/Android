package com.duckduckgo.app.browser.omnibar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmnibarLeadingIconVisibilityTest {

    @Test
    fun whenNotDuckAiModeThenFireShownAndPlusHidden() {
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = false, isNativeChatInputEnabled = true))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = false, isNativeChatInputEnabled = true))
    }

    @Test
    fun whenNotDuckAiModeAndNativeChatInputDisabledThenFireShownAndPlusHidden() {
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = false, isNativeChatInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = false, isNativeChatInputEnabled = false))
    }

    @Test
    fun whenDuckAiModeAndNativeChatInputEnabledThenPlusShownAndFireHidden() {
        assertTrue(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = true, isNativeChatInputEnabled = true))
        assertFalse(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = true, isNativeChatInputEnabled = true))
    }

    @Test
    fun whenDuckAiModeAndNativeChatInputDisabledThenFireShownAndPlusHidden() {
        // Regression: with nativeChatInput off (the web-input fallback) the user must keep the
        // fire button in the omnibar, not the + button, even while in a Duck.ai view.
        assertTrue(shouldShowFireIcon(showFireIcon = true, isDuckAiMode = true, isNativeChatInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = true, isNativeChatInputEnabled = false))
    }

    @Test
    fun whenFireIconHiddenThenNeitherFireNorPlusShown() {
        assertFalse(shouldShowFireIcon(showFireIcon = false, isDuckAiMode = true, isNativeChatInputEnabled = true))
        assertFalse(shouldShowPlusIcon(showFireIcon = false, isDuckAiMode = true, isNativeChatInputEnabled = true))
        assertFalse(shouldShowFireIcon(showFireIcon = false, isDuckAiMode = false, isNativeChatInputEnabled = false))
        assertFalse(shouldShowPlusIcon(showFireIcon = false, isDuckAiMode = false, isNativeChatInputEnabled = false))
    }

    @Test
    fun whenFireIconShownThenExactlyOneOfFireOrPlusIsVisible() {
        for (duckAi in listOf(true, false)) {
            for (nativeChatInput in listOf(true, false)) {
                val fire = shouldShowFireIcon(showFireIcon = true, isDuckAiMode = duckAi, isNativeChatInputEnabled = nativeChatInput)
                val plus = shouldShowPlusIcon(showFireIcon = true, isDuckAiMode = duckAi, isNativeChatInputEnabled = nativeChatInput)
                assertTrue("exactly one leading icon expected for duckAi=$duckAi nativeChatInput=$nativeChatInput", fire xor plus)
            }
        }
    }
}
