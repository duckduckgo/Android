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

package com.duckduckgo.app.browser.nativeinput

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.NativeInputOmnibarMetrics
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealNativeInputManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val animator: NativeInputAnimator = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val queryUrlPredictor: QueryUrlPredictor = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val pixel: Pixel = mock()
    private val omnibarMetrics: NativeInputOmnibarMetrics = mock()
    private val nativeInputStateBugKillSwitch = FakeFeatureToggleFactory.create(NativeInputStateBugKillSwitch::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rootView: ViewGroup = FrameLayout(context)
    private val omnibar: Omnibar = mock()
    private val lifecycleOwner = FakeLifecycleOwner()

    private lateinit var testee: RealNativeInputManager

    @Before
    fun setUp() {
        testee = RealNativeInputManager(
            duckChat,
            animator,
            voiceSearchAvailability,
            globalActivityStarter,
            queryUrlPredictor,
            duckAiFeatureState,
            pixel,
            nativeInputStateBugKillSwitch,
            omnibarMetrics,
        )
    }

    @Test
    fun whenDuckAiModeAndNativeChatInputDisabledThenShowNativeInputRemovesWidget() {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(false))
        whenever(omnibar.viewMode).thenReturn(Omnibar.ViewMode.DuckAI)
        testee.init(omnibar, rootView, lifecycleOwner)
        rootView.addView(View(context).apply { id = R.id.inputModeTopRoot })

        showNativeInput()

        assertNull(rootView.findViewById<View?>(R.id.inputModeTopRoot))
    }

    @Test
    fun whenNativeInputFieldDisabledThenShowNativeInputLeavesWidgetUntouched() {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(false))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(false))
        testee.init(omnibar, rootView, lifecycleOwner)
        rootView.addView(View(context).apply { id = R.id.inputModeTopRoot })

        showNativeInput()

        assertNotNull(rootView.findViewById<View?>(R.id.inputModeTopRoot))
    }

    @Test
    fun whenNativeChatInputFlipsOffInDuckAiModeThenWidgetRemoved() {
        val nativeChatInputEnabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(nativeChatInputEnabled)
        whenever(omnibar.viewMode).thenReturn(Omnibar.ViewMode.DuckAI)
        testee.init(omnibar, rootView, lifecycleOwner)
        rootView.addView(View(context).apply { id = R.id.inputModeTopRoot })

        nativeChatInputEnabled.value = false

        assertNull(rootView.findViewById<View?>(R.id.inputModeTopRoot))
    }

    private fun showNativeInput() {
        testee.showNativeInput(
            tabId = "tab",
            layoutInflater = LayoutInflater.from(context),
            lifecycleOwner = lifecycleOwner,
            tabs = mock<LiveData<List<TabEntity>>>(),
            currentTabUrl = emptyFlow(),
            query = "",
            callbacks = mock<NativeInputCallbacks>(),
        )
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun whenChatHeaderUpgradeTappedByFreeUserThenPixelFiredWithFreeTier() {
        testee.fireChatHeaderUpgradeTapped(DuckAiTier.Free)

        verify(pixel).fire(
            AppPixelName.AI_CHAT_UNIFIED_INPUT_CHAT_HEADER_UPGRADE_TAPPED,
            mapOf("user_tier" to "free"),
        )
    }

    @Test
    fun whenChatHeaderUpgradeTappedByPaidUserThenPixelFiredWithPlusTier() {
        testee.fireChatHeaderUpgradeTapped(DuckAiTier.Paid)

        verify(pixel).fire(
            AppPixelName.AI_CHAT_UNIFIED_INPUT_CHAT_HEADER_UPGRADE_TAPPED,
            mapOf("user_tier" to "plus"),
        )
    }

    @Test
    fun whenUrlIsNullThenIsExistingDuckAiChatFalse() {
        assertFalse(testee.isExistingDuckAiChat(null))
    }

    @Test
    fun whenUrlIsBlankThenIsExistingDuckAiChatFalse() {
        assertFalse(testee.isExistingDuckAiChat(""))
        assertFalse(testee.isExistingDuckAiChat("   "))
    }

    @Test
    fun whenUrlIsNotDuckAiThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(false)

        assertFalse(testee.isExistingDuckAiChat("https://example.com/?chatID=abcd"))
    }

    @Test
    fun whenDuckAiUrlWithoutChatIdThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertFalse(testee.isExistingDuckAiChat("https://duck.ai/"))
    }

    @Test
    fun whenDuckAiUrlWithBlankChatIdThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertFalse(testee.isExistingDuckAiChat("https://duck.ai/?chatID="))
    }

    @Test
    fun whenDuckAiUrlWithChatIdThenIsExistingDuckAiChatTrue() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertTrue(testee.isExistingDuckAiChat("https://duck.ai/?chatID=abc-123"))
    }

    @Test
    fun whenIsDuckChatUrlChecksParsedUriThenCheckedWithSameUri() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        val raw = "https://duck.ai/chat?chatID=xyz"

        testee.isExistingDuckAiChat(raw)

        verify(duckChat).isDuckChatUrl(Uri.parse(raw))
    }

    @Test
    fun whenUrlIsNullThenExtractDuckAiChatIdNull() {
        assertNull(testee.extractDuckAiChatId(null))
    }

    @Test
    fun whenUrlIsBlankThenExtractDuckAiChatIdNull() {
        assertNull(testee.extractDuckAiChatId(""))
        assertNull(testee.extractDuckAiChatId("   "))
    }

    @Test
    fun whenUrlIsNotDuckAiThenExtractDuckAiChatIdNull() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(false)
        assertNull(testee.extractDuckAiChatId("https://example.com/?chatID=abcd"))
    }

    @Test
    fun whenDuckAiUrlWithoutChatIdThenExtractDuckAiChatIdNull() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        assertNull(testee.extractDuckAiChatId("https://duck.ai/"))
    }

    @Test
    fun whenDuckAiUrlWithBlankChatIdThenExtractDuckAiChatIdNull() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        assertNull(testee.extractDuckAiChatId("https://duck.ai/?chatID="))
    }

    @Test
    fun whenDuckAiUrlWithChatIdThenExtractDuckAiChatIdReturnsValue() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        assertEquals("abc-123", testee.extractDuckAiChatId("https://duck.ai/?chatID=abc-123"))
    }
}
