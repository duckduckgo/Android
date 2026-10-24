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

package com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.FakeNativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AttachmentPrivacyFooterPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val inputState = MutableStateFlow(NativeInputState.zero().copy(hasAttachments = true))
    private val stateProvider: NativeInputStateProvider = mock()
    private val hostContext = MutableStateFlow(duckAiContext())
    private val host = FakeNativeInputFooterHost()
    private val browserNav: BrowserNav = mock()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AttachmentPrivacyDisclaimerStore
    private lateinit var testee: AttachmentPrivacyFooterPlugin

    @Before
    fun setUp() {
        whenever(stateProvider.state).thenReturn(inputState)
        feature.attachmentPrivacyDisclaimer().setRawStoredState(Toggle.State(enable = true))
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("attachment_privacy_${UUID.randomUUID()}") },
        )
        store = AttachmentPrivacyDisclaimerStore(dataStore)
        testee = AttachmentPrivacyFooterPlugin(
            duckChatFeature = feature,
            stateProvider = stateProvider,
            store = store,
            browserNav = browserNav,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenAttachmentStagedAndNeverShownThenFooterVisible() = runTest {
        testee.createFooter(context, hostContext, host).state.test {
            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoAttachmentStagedThenFooterHidden() = runTest {
        inputState.value = inputState.value.copy(hasAttachments = false)

        testee.createFooter(context, hostContext, host).state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFlagOffThenFooterHidden() = runTest {
        feature.attachmentPrivacyDisclaimer().setRawStoredState(Toggle.State(enable = false))

        testee.createFooter(context, hostContext, host).state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchSelectedOrEditingOrUnfocusedThenFooterHidden() = runTest {
        testee.createFooter(context, hostContext, host).state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(isDuckAiSelected = false)
            assertFalse(awaitItem().visible)

            hostContext.value = duckAiContext(isEditing = true)
            hostContext.value = duckAiContext(isInputFocused = false)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAlreadyShownOnDeviceThenFooterHidden() = runTest {
        store.markShown()

        testee.createFooter(context, hostContext, host).state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDisplayedThenMarkedShownAndStaysUpUntilPromptSubmitted() = runTest {
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)
            footer.onDisplayed(true)
            assertTrue(store.shown.first())
            expectNoEvents()

            inputState.value = inputState.value.copy(hasAttachments = false)
            assertFalse(awaitItem().visible)
            inputState.value = inputState.value.copy(hasAttachments = true)
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(promptSubmissions = 1)
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFooterIsCreatedThenItShowsTheDisclaimerWithLearnMoreLink() {
        val footer = testee.createFooter(context, hostContext, host)
        val message = footer.view.findViewById<TextView>(R.id.attachmentPrivacyFooterMessage).text as Spanned

        assertEquals("Files are automatically scanned for illegal content. Flagged chats have limited data retention. Learn more", message.toString())
        val link = message.getSpans(0, message.length, ClickableSpan::class.java).single()
        assertEquals("Learn more", message.subSequence(message.getSpanStart(link), message.getSpanEnd(link)).toString())
    }

    @Test
    fun whenLearnMoreTappedThenHelpPageOpensInNewTab() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        activity.setTheme(com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)
        whenever(browserNav.openInNewTab(any(), any(), anyOrNull())).thenReturn(Intent("open-in-new-tab"))
        val footer = testee.createFooter(activity, hostContext, host)
        val message = footer.view.findViewById<TextView>(R.id.attachmentPrivacyFooterMessage).text as Spanned

        message.getSpans(0, message.length, ClickableSpan::class.java).single().onClick(footer.view)

        verify(browserNav).openInNewTab(any(), eq("https://duckduckgo.com/duckduckgo-help-pages/duckai/ai-chat-privacy"), anyOrNull())
        assertEquals("open-in-new-tab", shadowOf(activity).nextStartedActivity.action)
    }

    private fun duckAiContext(
        isDuckAiSelected: Boolean = true,
        isEditing: Boolean = false,
        isInputFocused: Boolean = true,
        promptSubmissions: Int = 0,
        inputContext: NativeInputState.InputContext = NativeInputState.InputContext.DUCK_AI,
    ) = NativeInputFooterContext(
        isDuckAiSelected = isDuckAiSelected,
        isEditing = isEditing,
        browserMode = BrowserMode.REGULAR,
        isInputFocused = isInputFocused,
        inputContext = inputContext,
        promptSubmissions = promptSubmissions,
    )
}
