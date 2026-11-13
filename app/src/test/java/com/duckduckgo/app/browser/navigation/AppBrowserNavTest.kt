/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.mode.DuckAiWidget
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.app.browser.mode.SelectedTextSearch
import com.duckduckgo.app.pixels.toPixelLaunchSourceValue
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBrowserNavTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testee = AppBrowserNav()

    @Test
    fun whenOpenInNewTabWithSourceTabIdThenIntentCarriesSourceTab() {
        val intent = testee.openInNewTab(context, "https://example.com", sourceTabId = "tab-123")

        assertEquals("tab-123", intent.getStringExtra(BrowserActivity.SOURCE_TAB_ID_EXTRA))
    }

    @Test
    fun whenOpenInNewTabWithoutSourceTabIdThenIntentHasNoSourceTab() {
        val intent = testee.openInNewTab(context, "https://example.com")

        assertNull(intent.getStringExtra(BrowserActivity.SOURCE_TAB_ID_EXTRA))
    }

    @Test
    fun whenOpenDuckChatWithImageGenerationThenIntentForcesImageGeneration() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.ImageGeneration())

        assertTrue(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertEquals(DUCK_AI_URL, intent.getStringExtra("DUCK_CHAT_URL"))
        assertTrue(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertEquals("IMAGE_GENERATION", intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
    }

    @Test
    fun whenOpenDuckChatWithCameraCaptureThenIntentCarriesCameraCaptureAndDoesNotForceImageGeneration() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.CameraCapture())

        assertEquals("CAMERA_CAPTURE", intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
    }

    @Test
    fun whenOpenDuckChatWithVoiceFromWidgetThenIntentIsVoiceLaunchWithoutUrl() {
        val intent = testee.openDuckChat(context, launch = DuckChatLaunch.Voice(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET))

        assertTrue(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertTrue(intent.getBooleanExtra("DUCK_CHAT_VOICE", false))
        assertNull(intent.getStringExtra("DUCK_CHAT_URL"))
        assertNull(intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
        assertEquals(DuckAiWidget.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
        assertEquals("WIDGET_DUCK_AI", intent.getStringExtra(DUCK_CHAT_ENTRY_POINT_EXTRA))
    }

    @Test
    fun whenOpenDuckChatWithOpenThenIntentIsNotVoice() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.Open())

        assertFalse(intent.getBooleanExtra("DUCK_CHAT_VOICE", false))
    }

    @Test
    fun whenOpenDuckChatWithOpenThenIntentIsPlainDuckChatLaunch() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.Open())

        assertTrue(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertNull(intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
        assertNull(intent.getStringExtra("DUCK_CHAT_TEXT_SELECTION_EXTRA"))
        assertFalse(intent.getBooleanExtra(BrowserActivity.OPEN_DUCK_CHAT_HISTORY, false))
    }

    @Test
    fun whenOpenDuckChatWithoutUrlThenIntentHasNoUrl() {
        val intent = testee.openDuckChat(context)

        assertTrue(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertNull(intent.getStringExtra("DUCK_CHAT_URL"))
    }

    @Test
    fun whenOpenDuckChatFromHomeScreenWidgetThenIntentRequiresRegularModeAndReportsWidgetEntryPoint() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.Open(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET))

        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
        assertEquals(DuckAiWidget.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
        assertEquals("WIDGET_DUCK_AI", intent.getStringExtra(DUCK_CHAT_ENTRY_POINT_EXTRA))
        assertNull(intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
    }

    @Test
    fun whenOpenDuckChatFromHomeScreenWidgetWithCameraCaptureThenIntentCarriesBoth() {
        val intent = testee.openDuckChat(
            context,
            duckChatUrl = DUCK_AI_URL,
            launch = DuckChatLaunch.CameraCapture(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
        )

        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
        assertEquals("CAMERA_CAPTURE", intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertEquals("WIDGET_DUCK_AI", intent.getStringExtra(DUCK_CHAT_ENTRY_POINT_EXTRA))
    }

    @Test
    fun whenOpenDuckChatFromHomeScreenWidgetWithImageGenerationThenIntentCarriesBoth() {
        val intent = testee.openDuckChat(
            context,
            duckChatUrl = DUCK_AI_URL,
            launch = DuckChatLaunch.ImageGeneration(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
        )

        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
        assertTrue(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertEquals("IMAGE_GENERATION", intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertEquals("WIDGET_DUCK_AI", intent.getStringExtra(DUCK_CHAT_ENTRY_POINT_EXTRA))
    }

    @Test
    fun whenOpenDuckChatFromInAppThenIntentDoesNotRequireRegularModeAndReportsNoEntryPoint() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.Open(DuckChatLaunchOrigin.IN_APP))

        assertFalse(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, true))
        assertEquals(InAppNavigation.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
        assertNull(intent.getStringExtra(DUCK_CHAT_ENTRY_POINT_EXTRA))
    }

    @Test
    fun whenOpenDuckChatWithChatHistoryFromHomeScreenWidgetThenIntentOpensHistoryInRegularModeWithoutDuckChat() {
        val intent = testee.openDuckChat(context, launch = DuckChatLaunch.ChatHistory(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET))

        assertTrue(intent.getBooleanExtra(BrowserActivity.OPEN_DUCK_CHAT_HISTORY, false))
        assertFalse(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertNull(intent.getStringExtra(NATIVE_INPUT_LAUNCH_ACTION_EXTRA))
        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
        assertEquals(DuckAiWidget.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
    }

    @Test
    fun whenOpenDuckChatWithChatHistoryFromInAppThenIntentDoesNotRequireRegularMode() {
        val intent = testee.openDuckChat(context, launch = DuckChatLaunch.ChatHistory(DuckChatLaunchOrigin.IN_APP))

        assertTrue(intent.getBooleanExtra(BrowserActivity.OPEN_DUCK_CHAT_HISTORY, false))
        assertFalse(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, true))
    }

    @Test
    fun whenOpenDuckChatWithContextualChatThenIntentCarriesContextualFlagAndSelection() {
        val intent = testee.openDuckChat(
            context,
            duckChatUrl = DUCK_AI_URL,
            launch = DuckChatLaunch.ContextualChat("selected"),
        )

        assertTrue(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
        assertEquals("selected", intent.getStringExtra("DUCK_CHAT_TEXT_SELECTION_EXTRA"))
        assertFalse(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
        assertEquals(InAppNavigation.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
    }

    @Test
    fun whenOpenDuckChatWithContextualChatWithoutSelectionThenIntentHasNoSelection() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.ContextualChat(null))

        assertTrue(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
        assertNull(intent.getStringExtra("DUCK_CHAT_TEXT_SELECTION_EXTRA"))
        assertEquals(InAppNavigation.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
    }

    @Test
    fun whenOpenDuckChatWithTextSelectionThenIntentIsNotContextualAndCarriesSelectionFromSelectedTextSource() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launch = DuckChatLaunch.TextSelection("selected"))

        assertFalse(intent.getBooleanExtra("DUCK_CHAT_CONTEXTUAL_EXTRA", false))
        assertEquals("selected", intent.getStringExtra("DUCK_CHAT_TEXT_SELECTION_EXTRA"))
        assertEquals(SelectedTextSearch.toPixelLaunchSourceValue(), intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE))
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val NATIVE_INPUT_LAUNCH_ACTION_EXTRA = "NATIVE_INPUT_LAUNCH_ACTION_EXTRA"
        const val DUCK_CHAT_ENTRY_POINT_EXTRA = "DUCK_CHAT_ENTRY_POINT_EXTRA"
    }
}
