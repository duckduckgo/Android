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
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.NativeInputEventListener
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputWidget
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
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
    private val duckChatInputModeState: DuckChatInputModeState = mock()
    private val pixel: Pixel = mock()
    private val nativeInputEventListener: NativeInputEventListener = mock()
    private val edgeToEdgeProvider: EdgeToEdgeProvider = mock()
    private val edgeToEdgeHandler = EdgeToEdgeHandler()
    private val nativeInputStateBugKillSwitch = FakeFeatureToggleFactory.create(NativeInputStateBugKillSwitch::class.java)
    private val nativeInputUrlClearingFeature = FakeFeatureToggleFactory.create(NativeInputUrlClearingFeature::class.java)
    private val nativeInputOmnibarFeature = FakeFeatureToggleFactory.create(NativeInputOmnibarFeature::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rootView: ViewGroup = FrameLayout(context)
    private val omnibar: Omnibar = mock()
    private val lifecycleOwner = FakeLifecycleOwner()

    private lateinit var testee: RealNativeInputManager

    private val inputModeCapabilityFlow = MutableStateFlow(NativeInputState.InputMode.SEARCH_AND_DUCK_AI)

    @Before
    fun setUp() {
        whenever(duckChatInputModeState.inputModeCapability).thenReturn(inputModeCapabilityFlow)
        whenever(duckChat.observeNativeInputNavBarEnabled()).thenReturn(MutableStateFlow(false))
        whenever(voiceSearchAvailability.observeVoiceSearchAvailability()).thenReturn(MutableStateFlow(false))
        nativeInputOmnibarFeature.self().setRawStoredState(State(enable = false))
        nativeInputUrlClearingFeature.self().setRawStoredState(State(enable = true))
        testee = RealNativeInputManager(
            duckChat,
            animator,
            voiceSearchAvailability,
            globalActivityStarter,
            queryUrlPredictor,
            duckAiFeatureState,
            duckChatInputModeState,
            pixel,
            nativeInputStateBugKillSwitch,
            nativeInputUrlClearingFeature,
            nativeInputOmnibarFeature,
            nativeInputEventListener,
            edgeToEdgeProvider,
            edgeToEdgeHandler,
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
    fun whenInputModeBecomesSearchOnlyWhileWidgetShownThenWidgetRemoved() {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(false))
        testee.init(omnibar, rootView, lifecycleOwner)
        rootView.addView(View(context).apply { id = R.id.inputModeTopRoot })

        inputModeCapabilityFlow.value = NativeInputState.InputMode.SEARCH_ONLY

        assertNull(rootView.findViewById<View?>(R.id.inputModeTopRoot))
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

    @Test
    fun whenWidgetRemovedThenNavBarAlsoRemoved() {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(false))
        whenever(omnibar.viewMode).thenReturn(Omnibar.ViewMode.DuckAI)
        testee.init(omnibar, rootView, lifecycleOwner)
        rootView.addView(View(context).apply { id = R.id.inputModeTopRoot })
        rootView.addView(View(context).apply { id = R.id.inputModeWidgetNavLayout })

        showNativeInput()

        assertNull(rootView.findViewById<View?>(R.id.inputModeWidgetNavLayout))
    }

    @Test
    fun whenChatSelectedWithUrlThenInputCleared() {
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()
        assertEquals("", input.text)
    }

    @Test
    fun whenSearchSelectedThenCachedUrlRestored() {
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()
        input.switchToSearch()

        assertEquals(URL, input.text)
        assertEquals(1, input.selectAllTextCalls)
    }

    @Test
    fun whenInputContainsSearchQueryThenSwitchingModesDoesNotClearInput() {
        val input = givenUrlCachingBound(text = QUERY)
        input.switchToDuckAi()
        input.switchToSearch()

        assertEquals(QUERY, input.text)
        assertEquals(0, input.selectAllTextCalls)
    }

    @Test
    fun whenPromptTypedInDuckAiThenCachedUrlNotRestored() {
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()
        input.text = QUERY
        input.switchToSearch()

        assertEquals(QUERY, input.text)
    }

    @Test
    fun whenUrlRestoredAndClearedThenCachedUrlCleared() {
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()
        input.switchToSearch()
        input.text = ""
        input.switchToDuckAi()
        input.switchToSearch()

        assertEquals("", input.text)
    }

    @Test
    fun whenSwitchingModesThenExistingListenersStillFire() {
        var duckAiListenerFired = false
        var searchListenerFired = false
        val input = givenUrlCachingBound(text = URL) {
            onChatSelected = { duckAiListenerFired = true }
            onSearchSelected = { searchListenerFired = true }
        }
        input.switchToDuckAi()
        input.switchToSearch()

        assertTrue(duckAiListenerFired)
        assertTrue(searchListenerFired)
    }

    @Test
    fun whenSwitchingToDuckAiThenListenerTextCleared() {
        var listenerText: String? = null
        val input = givenUrlCachingBound(text = URL) {
            onChatSelected = { listenerText = text }
        }
        input.switchToDuckAi()

        assertEquals("", listenerText)
    }

    @Test
    fun whenUserTypedUrlIntoEmptyInputThenSwitchingToDuckAiDoesNotClearIt() {
        val input = givenUrlCachingBound(text = "")
        input.text = URL
        input.switchToDuckAi()

        assertEquals(URL, input.text)
    }

    @Test
    fun whenUserEditedPrefilledUrlThenSwitchingToDuckAiDoesNotClearIt() {
        val input = givenUrlCachingBound(text = URL)
        input.text = OTHER_URL
        input.switchToDuckAi()

        assertEquals(OTHER_URL, input.text)
    }

    @Test
    fun whenRestoredUrlIsUnchangedThenSwitchingToDuckAiClearsIt() {
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()
        input.switchToSearch()
        input.switchToDuckAi()

        assertEquals("", input.text)
    }

    @Test
    fun whenUrlClearingKillSwitchDisabledThenInputNotCleared() {
        nativeInputUrlClearingFeature.self().setRawStoredState(State(enable = false))
        val input = givenUrlCachingBound(text = URL)
        input.switchToDuckAi()

        assertEquals(URL, input.text)
    }

    @Test
    fun whenInDuckAiModeThenInputNotCleared() {
        val input = givenUrlCachingBound(text = URL, duckAiMode = true)
        input.switchToDuckAi()

        assertEquals(URL, input.text)
    }

    private fun givenUrlCachingBound(
        text: String,
        duckAiMode: Boolean = false,
        existingListeners: FakeInput.() -> Unit = {},
    ): FakeInput {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(true))
        whenever(omnibar.viewMode).thenReturn(if (duckAiMode) Omnibar.ViewMode.DuckAI else Omnibar.ViewMode.Browser(null))
        whenever(queryUrlPredictor.isUrl(URL)).thenReturn(true)
        whenever(queryUrlPredictor.isUrl(OTHER_URL)).thenReturn(true)
        whenever(queryUrlPredictor.isUrl(QUERY)).thenReturn(false)
        testee.init(omnibar, rootView, lifecycleOwner)

        val input = FakeInput(context).apply {
            id = R.id.inputModeWidget
            this.text = text
            existingListeners()
        }
        testee.bindUrlCaching(FrameLayout(context).apply { addView(input) })
        return input
    }

    private class FakeInput(
        context: Context,
        delegate: NativeInputWidget = mock(),
    ) : FrameLayout(context), NativeInputWidget by delegate {
        override var text: String = ""
        override var onSearchSelected: ((animate: Boolean) -> Unit)? = null
        override var onChatSelected: ((animate: Boolean) -> Unit)? = null
        var selectAllTextCalls = 0

        fun switchToDuckAi() {
            onChatSelected?.invoke(true)
        }

        fun switchToSearch() {
            onSearchSelected?.invoke(true)
        }

        override fun selectAllText() {
            selectAllTextCalls++
        }
    }

    @Test
    fun whenBlankDuckAiVoiceResultFollowedByTypedSubmissionThenTypedSubmissionUsesAddressBarPromptEntryPoint() {
        val entryPoints = mutableListOf<DuckChatEntryPoint>()
        val widget = showVoiceTestWidget { _, entryPoint -> entryPoints += entryPoint }

        testee.handleDuckAiVoiceResult("")
        widget.submitMessage("typed query")

        assertEquals(listOf(DuckChatEntryPoint.ADDRESS_BAR_PROMPT), entryPoints)
    }

    @Test
    fun whenWhitespaceDuckAiVoiceResultFollowedByTypedSubmissionThenTypedSubmissionUsesAddressBarPromptEntryPoint() {
        val entryPoints = mutableListOf<DuckChatEntryPoint>()
        val widget = showVoiceTestWidget { _, entryPoint -> entryPoints += entryPoint }

        testee.handleDuckAiVoiceResult("   ")
        widget.submitMessage("typed query")

        assertEquals(listOf(DuckChatEntryPoint.ADDRESS_BAR_PROMPT), entryPoints)
    }

    @Test
    fun whenNonBlankDuckAiVoiceResultThenSubmissionUsesVoiceEntryPoint() {
        val entryPoints = mutableListOf<DuckChatEntryPoint>()
        showVoiceTestWidget { _, entryPoint -> entryPoints += entryPoint }

        testee.handleDuckAiVoiceResult("voice query")

        assertEquals(listOf(DuckChatEntryPoint.VOICE), entryPoints)
    }

    @Test
    fun whenTypedSubmissionFollowsSuccessfulDuckAiVoiceSubmissionThenTypedSubmissionUsesAddressBarPromptEntryPoint() {
        val entryPoints = mutableListOf<DuckChatEntryPoint>()
        val onSubmitted = { _: String, entryPoint: DuckChatEntryPoint -> entryPoints += entryPoint }
        showVoiceTestWidget(onSubmitted)
        testee.handleDuckAiVoiceResult("voice query")

        val widget = showVoiceTestWidget(onSubmitted)
        widget.selectChatTab()
        widget.submitMessage("typed query")

        assertEquals(
            listOf(DuckChatEntryPoint.VOICE, DuckChatEntryPoint.ADDRESS_BAR_PROMPT),
            entryPoints,
        )
    }

    private fun showVoiceTestWidget(onDuckAiQuerySubmitted: (String, DuckChatEntryPoint) -> Unit): TestNativeInputWidget {
        whenever(duckChat.observeNativeInputFieldUserSettingEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckAiFeatureState.showVoiceSearchToggle).thenReturn(MutableStateFlow(false))
        whenever(duckAiFeatureState.showVoiceChatEntry).thenReturn(MutableStateFlow(false))
        whenever(omnibar.viewMode).thenReturn(Omnibar.ViewMode.NewTab)
        whenever(omnibar.getText()).thenReturn("")
        testee.init(omnibar, rootView, lifecycleOwner)
        if (rootView.findViewById<View?>(R.id.includeNewBrowserTab) == null) {
            rootView.addView(FrameLayout(context).apply { id = R.id.includeNewBrowserTab })
        }

        val widget = TestNativeInputWidget(context).apply { id = R.id.inputModeWidget }
        val widgetView = FrameLayout(context).apply { addView(widget) }
        val layoutInflater: LayoutInflater = mock()
        whenever(layoutInflater.inflate(any<Int>(), any<ViewGroup>(), any<Boolean>())).thenReturn(widgetView)
        testee.showNativeInput(
            tabId = "tab",
            layoutInflater = layoutInflater,
            lifecycleOwner = lifecycleOwner,
            tabs = mock<LiveData<List<TabEntity>>>(),
            currentTabUrl = emptyFlow(),
            callbacks = NativeInputCallbacks(
                onSearchTextChanged = {},
                onSearchSubmitted = {},
                onDuckAiChatSubmitted = { _, _, _, _, _, _ -> },
                onChatSuggestionSelected = {},
                onDuckAiQuerySubmitted = onDuckAiQuerySubmitted,
                onClearAutocomplete = {},
                onStopTapped = {},
            ),
        )
        return widget
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

    private companion object {
        private const val URL = "https://example.com"
        private const val OTHER_URL = "https://foo.com"
        private const val QUERY = "query"
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class TestNativeInputWidget(
        context: Context,
        delegate: NativeInputWidget = mock(),
    ) : View(context), NativeInputWidget by delegate {
        private var chatTabSelected = false
        private var onChatSubmitted: ((String) -> Unit)? = null

        override var text: String = ""
        override var nextDuckAiEntryPoint: DuckChatEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT

        override fun bindInputEvents(
            onSearchTextChanged: (String) -> Unit,
            onSearchSubmitted: (String) -> Unit,
            onChatSubmitted: (String) -> Unit,
            onInputTextEmptyChanged: (isEmpty: Boolean) -> Unit,
        ) {
            this.onChatSubmitted = onChatSubmitted
        }

        override fun selectChatTab() {
            chatTabSelected = true
        }

        override fun isChatTabSelected(): Boolean = chatTabSelected

        override fun submitMessage(message: String?) {
            message?.trim()?.takeIf { it.isNotEmpty() }?.let { onChatSubmitted?.invoke(it) }
        }

        override fun asView(): View = this
    }

    @Test
    fun whenChatHeaderUpgradeTappedByFreeUserThenPixelFiredWithFreeTierAndOrigin() {
        testee.fireChatHeaderUpgradeTapped(DuckAiTier.Free)

        verify(pixel).fire(
            AppPixelName.AI_CHAT_UNIFIED_INPUT_CHAT_HEADER_UPGRADE_TAPPED,
            mapOf("user_tier" to "free", "origin" to "funnel_duckai_android__freelabel"),
        )
    }

    @Test
    fun whenChatHeaderUpgradeTappedByPaidUserThenPixelFiredWithPlusTierAndOrigin() {
        testee.fireChatHeaderUpgradeTapped(DuckAiTier.Paid)

        verify(pixel).fire(
            AppPixelName.AI_CHAT_UNIFIED_INPUT_CHAT_HEADER_UPGRADE_TAPPED,
            mapOf("user_tier" to "plus", "origin" to "funnel_duckai_android__freelabel"),
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
