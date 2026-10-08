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
package com.duckduckgo.duckchat.impl.nativeinput.footer.terms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.FakeNativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(AndroidJUnit4::class)
class TermsFooterPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val accepted = MutableStateFlow(false)
    private val termsRepository: DuckAiTermsRepository = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val hostContext = MutableStateFlow(duckAiContext())
    private val browserNav: BrowserNav = mock()
    private val testee = TermsFooterPlugin(termsRepository, feature, browserNav)

    @Before
    fun setUp() {
        whenever(termsRepository.observeTermsAccepted(BrowserMode.REGULAR)).thenReturn(accepted)
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenTermsNotAcceptedThenFooterVisible() = runTest {
        testee.createFooter(context, hostContext, FakeNativeInputFooterHost()).state.test {
            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTermsAcceptedThenFooterHides() = runTest {
        testee.createFooter(context, hostContext, FakeNativeInputFooterHost()).state.test {
            assertTrue(awaitItem().visible)

            accepted.value = true

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFlagOffThenFooterHidden() = runTest {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = false))

        testee.createFooter(context, hostContext, FakeNativeInputFooterHost()).state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchModeSelectedThenFooterHidden() = runTest {
        hostContext.value = duckAiContext(isDuckAiSelected = false)

        testee.createFooter(context, hostContext, FakeNativeInputFooterHost()).state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenEditingOrInputNotFocusedThenFooterHidden() = runTest {
        testee.createFooter(context, hostContext, FakeNativeInputFooterHost()).state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(isEditing = true)
            assertFalse(awaitItem().visible)

            hostContext.value = duckAiContext(isInputFocused = false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFooterIsCreatedThenItShowsTheLegalCopyNamingTheAskButton() {
        val footer = testee.createFooter(context, hostContext, FakeNativeInputFooterHost())
        val message = footer.view.findViewById<TextView>(R.id.termsFooterMessage).text

        assertEquals(
            "DuckDuckGo anonymizes your chats. By clicking 'Ask' you agree to our Privacy Policy & Terms of Service.",
            message.toString(),
        )
    }

    @Test
    fun whenFooterIsCreatedThenPrivacyPolicyAndTermsOfServiceAreLinks() {
        val footer = testee.createFooter(context, hostContext, FakeNativeInputFooterHost())
        val message = footer.view.findViewById<TextView>(R.id.termsFooterMessage).text as Spanned

        val linked = message.getSpans(0, message.length, ClickableSpan::class.java)
            .sortedBy { message.getSpanStart(it) }
            .map { message.subSequence(message.getSpanStart(it), message.getSpanEnd(it)).toString() }

        assertEquals(listOf("Privacy Policy", "Terms of Service"), linked)
    }

    @Test
    fun whenFooterIsCreatedThenItUsesTheDesignPaddings() {
        val footer = testee.createFooter(context, hostContext, FakeNativeInputFooterHost())
        val row = (footer.view as ViewGroup).getChildAt(0)

        val start = context.resources.getDimensionPixelSize(R.dimen.termsFooterStartPadding)
        val end = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        val vertical = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_3)
        assertEquals(listOf(start, vertical, end, vertical), listOf(row.paddingStart, row.paddingTop, row.paddingEnd, row.paddingBottom))
    }

    @Test
    fun whenFooterIsCreatedThenItShowsTheShieldIconBeforeTheMessage() {
        val footer = testee.createFooter(context, hostContext, FakeNativeInputFooterHost())
        val row = (footer.view as ViewGroup).getChildAt(0) as ViewGroup

        assertEquals(R.id.termsFooterIcon, row.getChildAt(0).id)
        assertEquals(R.id.termsFooterMessage, row.getChildAt(1).id)
    }

    @Test
    fun whenALinkIsTappedThenItOpensInANewTab() {
        // The footer is built with the input widget's context, which wraps an Activity.
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        activity.setTheme(com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)
        whenever(browserNav.openInNewTab(any(), any(), anyOrNull())).thenReturn(Intent("open-in-new-tab"))
        val footer = testee.createFooter(activity, hostContext, FakeNativeInputFooterHost())
        val message = footer.view.findViewById<TextView>(R.id.termsFooterMessage).text as Spanned
        val link = message.getSpans(0, message.length, ClickableSpan::class.java).first()

        link.onClick(footer.view)

        verify(browserNav).openInNewTab(any(), eq("https://duckduckgo.com/duckai/privacy-terms"), anyOrNull())
        assertEquals("open-in-new-tab", shadowOf(activity).nextStartedActivity.action)
    }

    private fun duckAiContext(
        isDuckAiSelected: Boolean = true,
        isEditing: Boolean = false,
        isInputFocused: Boolean = true,
    ) = NativeInputFooterContext(
        isDuckAiSelected = isDuckAiSelected,
        isEditing = isEditing,
        browserMode = BrowserMode.REGULAR,
        isInputFocused = isInputFocused,
        inputContext = NativeInputState.InputContext.DUCK_AI,
    )
}
