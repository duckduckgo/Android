/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.dispatchers

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.global.intentText
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsNoParams
import com.duckduckgo.sync.api.setup.SyncUrlIdentifier
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class IntentDispatcherViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockCustomTabDetector: CustomTabDetector = mock()
    private val mockIntent: Intent = mock()
    private val emailProtectionLinkVerifier: EmailProtectionLinkVerifier = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val syncUrlIdentifier: SyncUrlIdentifier = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    private lateinit var testee: IntentDispatcherViewModel

    @Before
    fun before() {
        testee = IntentDispatcherViewModel(
            customTabDetector = mockCustomTabDetector,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            emailProtectionLinkVerifier = emailProtectionLinkVerifier,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
            syncUrlIdentifier = syncUrlIdentifier,
            appBuildConfig = mockAppBuildConfig,
        )

        whenever(syncUrlIdentifier.shouldDelegateToSyncSetup(anyOrNull())).thenReturn(false)
    }

    @Test
    fun whenIntentReceivedWithSessionThenCustomTabIsRequested() = runTest {
        val text = "url"
        val toolbarColor = 100
        configureHasSession(true)
        whenever(mockIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)).thenReturn(toolbarColor)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(toolbarColor, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithoutSessionThenCustomTabIsNotRequested() = runTest {
        val text = "url"
        configureHasSession(false)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(DEFAULT_COLOR, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsEmailProtectionVerificationThenCustomTabIsNotRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(true)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsNotEmailProtectionVerificationThenCustomTabIsRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(false)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndUrlContainingSpacesThenSpacesAreReplacedAndCustomTabIsRequested() = runTest {
        val urlWithSpaces =
            """
                https://mastodon.social/oauth/authorize?client_id=AcfPDZlcKUjwIatVtMt8B8cmdW-w1CSOR6_rYS_6Kxs&scope=read write push&redirect_uri=mastify://oauth&response_type=code
            """.trimIndent()
        val expectedUrl =
            """
                https://mastodon.social/oauth/authorize?client_id=AcfPDZlcKUjwIatVtMt8B8cmdW-w1CSOR6_rYS_6Kxs&scope=read%20write%20push&redirect_uri=mastify://oauth&response_type=code
            """.trimIndent()
        whenever(mockIntent.intentText).thenReturn(urlWithSpaces)
        configureHasSession(true)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(expectedUrl, state.intentText)
        }
    }

    @Test
    fun whenIntentReceivedWithNoSessionAndIntentTextContainingSpacesAndNotStartingWithHttpSchemaThenNoChangesAreMadeToTheIntent() = runTest {
        val intentTextWithSpaces =
            """
                Voyager 1 is still bringing us surprises from the very edge of our solar system https://www.independent.co.uk/space/voyager-1-nasa-latest-solar-system-b2535462.html
            """.trimIndent()
        whenever(mockIntent.intentText).thenReturn(intentTextWithSpaces)
        configureHasSession(false)
        configureIsEmailProtectionLink(false)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(intentTextWithSpaces, state.intentText)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndIntentTextContainingSpacesAndNotStartingWithHttpSchemaThenNoChangesAreMadeToTheIntent() = runTest {
        val intentTextWithSpaces =
            """
                Voyager 1 is still bringing us surprises from the very edge of our solar system https://www.independent.co.uk/space/voyager-1-nasa-latest-solar-system-b2535462.html
            """.trimIndent()
        whenever(mockIntent.intentText).thenReturn(intentTextWithSpaces)
        configureHasSession(true)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(intentTextWithSpaces, state.intentText)
        }
    }

    @Test
    fun `when Intent received with session and intent text is a DDG domain then custom tab is not requested`() = runTest {
        val text = "some DDG url"
        val toolbarColor = 100
        configureHasSession(true)
        whenever(mockIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)).thenReturn(toolbarColor)
        whenever(mockIntent.intentText).thenReturn(text)
        whenever(duckDuckGoUrlDetector.isDuckDuckGoUrl(text)).thenReturn(true)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedForSyncPairingUrlThenCustomTabIsNotRequested() = runTest {
        val intentUrl = SyncBarcodeUrl.URL_BASE
        whenever(mockIntent.intentText).thenReturn(intentUrl)
        whenever(syncUrlIdentifier.shouldDelegateToSyncSetup(intentUrl)).thenReturn(true)
        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = true)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(intentUrl, state.intentText)
        }
    }

    @Test
    fun whenIntentReceivedWithDuckPlayerDataAndInternalBuildThenActivityParamsAreSet() = runTest {
        val uri = Uri.parse("duck://settings.player")
        whenever(mockIntent.data).thenReturn(uri)
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(DuckPlayerSettingsNoParams, state.activityParams)
        }
    }

    @Test
    fun whenIntentReceivedWithDuckPlayerDataAndNotInternalBuildThenActivityParamsAreNotSet() = runTest {
        val uri = Uri.parse("duck://settings.player")
        whenever(mockIntent.data).thenReturn(uri)
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertNull(state.activityParams)
        }
    }

    private fun configureHasSession(returnValue: Boolean) {
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)).thenReturn(returnValue)
    }

    private fun configureIsEmailProtectionLink(returnValue: Boolean) {
        whenever(emailProtectionLinkVerifier.shouldDelegateToInContextView(anyOrNull(), any())).thenReturn(returnValue)
    }

    private companion object {
        private const val DEFAULT_COLOR = 0
    }
}
