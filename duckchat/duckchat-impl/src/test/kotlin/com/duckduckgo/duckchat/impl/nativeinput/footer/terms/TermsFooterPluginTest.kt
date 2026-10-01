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

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.FakeNativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
    private val testee = TermsFooterPlugin(termsRepository, feature)

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

    private fun duckAiContext(
        isDuckAiSelected: Boolean = true,
        isEditing: Boolean = false,
        isInputFocused: Boolean = true,
    ) = NativeInputFooterContext(
        isDuckAiSelected = isDuckAiSelected,
        isEditing = isEditing,
        browserMode = BrowserMode.REGULAR,
        isInputFocused = isInputFocused,
    )
}
