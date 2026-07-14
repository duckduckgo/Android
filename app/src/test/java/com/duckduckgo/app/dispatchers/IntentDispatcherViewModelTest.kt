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
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayerSettingsNoParams
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.pdf.InlinePdfHandler
import com.duckduckgo.app.browser.pdf.LocalPdfResult
import com.duckduckgo.app.browser.pdf.PdfErrorType
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.Toggle
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class IntentDispatcherViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockIntent: Intent = mock()
    private val emailProtectionLinkVerifier: EmailProtectionLinkVerifier = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val syncUrlIdentifier: SyncUrlIdentifier = mock()
    private val duckChat: DuckChat = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val inlinePdfHandler: InlinePdfHandler = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()

    private lateinit var testee: IntentDispatcherViewModel

    @Before
    fun before() {
        testee = IntentDispatcherViewModel(
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            emailProtectionLinkVerifier = emailProtectionLinkVerifier,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
            syncUrlIdentifier = syncUrlIdentifier,
            duckChat = duckChat,
            appBuildConfig = mockAppBuildConfig,
            inlinePdfHandler = inlinePdfHandler,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
        )

        whenever(syncUrlIdentifier.shouldDelegateToSyncSetup(anyOrNull())).thenReturn(false)
        val pdfViewerToggle = mockToggle(enabled = false)
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(pdfViewerToggle)
    }

    @Test
    fun whenIntentReceivedWithSessionThenCustomTabIsRequested() = runTest {
        val text = "url"
        val toolbarColor = 100
        configureHasSession(true)
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR)).thenReturn(true)
        whenever(mockIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)).thenReturn(toolbarColor)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(toolbarColor, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionThenCustomTabIsRequestedWithNullToolbarColor() = runTest {
        val text = "url"
        configureHasSession(true)
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR)).thenReturn(false)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(null, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithoutSessionThenCustomTabIsNotRequested() = runTest {
        val text = "url"
        configureHasSession(false)
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR)).thenReturn(false)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(null, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsEmailProtectionVerificationThenCustomTabIsNotRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(true)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsNotEmailProtectionVerificationThenCustomTabIsRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(false)

        testee.onIntentReceived(mockIntent, isExternal = false)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
        }
    }

    @Test
    fun `when Intent received with session and intent text is a duck ai url then custom tab is not requested`() = runTest {
        val text = "https://duck.ai/?q=hello"
        configureHasSession(true)
        whenever(mockIntent.intentText).thenReturn(text)
        whenever(duckChat.isDuckChatUrl(text.toUri())).thenReturn(true)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(text, state.intentText)
        }
    }

    @Test
    fun `when Intent received with session and intent text is not a duck ai url then custom tab is requested`() = runTest {
        val text = "https://example.com"
        configureHasSession(true)
        whenever(mockIntent.intentText).thenReturn(text)
        whenever(duckChat.isDuckChatUrl(text.toUri())).thenReturn(false)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedForSyncPairingUrlThenCustomTabIsNotRequested() = runTest {
        val intentUrl = SyncBarcodeUrl.URL_BASE
        whenever(mockIntent.intentText).thenReturn(intentUrl)
        whenever(syncUrlIdentifier.shouldDelegateToSyncSetup(intentUrl)).thenReturn(true)
        testee.onIntentReceived(mockIntent, isExternal = true)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

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

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertNull(state.activityParams)
        }
    }

    @Test
    fun `when VIEW intent with application pdf type and content URI and flag enabled then openLocalPdf is true`() = runTest {
        val contentUri = Uri.parse("content://com.example.provider/files/doc.pdf")
        val cachedUri = Uri.parse("file:///data/user/0/com.example/cache/pdf_cache/doc.pdf")
        val enabledToggle = mockToggle(enabled = true)
        whenever(mockIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(mockIntent.data).thenReturn(contentUri)
        whenever(mockIntent.type).thenReturn("application/pdf")
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(enabledToggle)
        whenever(inlinePdfHandler.cacheLocalPdf(contentUri)).thenReturn(LocalPdfResult.Success(cachedUri, "doc.pdf"))

        testee.onIntentReceived(mockIntent, isExternal = true)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.openLocalPdf)
            assertEquals(cachedUri.toString(), state.intentText)
            assertFalse(state.localPdfError)
            assertEquals("doc.pdf", state.localPdfName)
        }
    }

    @Test
    fun `when VIEW intent with pdf path and null type and flag enabled then openLocalPdf is true`() = runTest {
        val contentUri = Uri.parse("content://com.example.provider/files/report.pdf")
        val cachedUri = Uri.parse("file:///data/user/0/com.example/cache/pdf_cache/report.pdf")
        val enabledToggle = mockToggle(enabled = true)
        whenever(mockIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(mockIntent.data).thenReturn(contentUri)
        whenever(mockIntent.type).thenReturn(null)
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(enabledToggle)
        whenever(inlinePdfHandler.cacheLocalPdf(contentUri)).thenReturn(LocalPdfResult.Success(cachedUri, "report.pdf"))

        testee.onIntentReceived(mockIntent, isExternal = true)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.openLocalPdf)
            assertEquals(cachedUri.toString(), state.intentText)
            assertEquals("report.pdf", state.localPdfName)
        }
    }

    @Test
    fun `when VIEW intent with application pdf type and flag enabled and cacheLocalPdf fails then localPdfError is true`() = runTest {
        val contentUri = Uri.parse("content://com.example.provider/files/doc.pdf")
        val enabledToggle = mockToggle(enabled = true)
        whenever(mockIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(mockIntent.data).thenReturn(contentUri)
        whenever(mockIntent.type).thenReturn("application/pdf")
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(enabledToggle)
        whenever(inlinePdfHandler.cacheLocalPdf(contentUri)).thenReturn(LocalPdfResult.Failure(PdfErrorType.UNKNOWN))

        testee.onIntentReceived(mockIntent, isExternal = true)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.localPdfError)
            assertFalse(state.openLocalPdf)
        }
    }

    @Test
    fun `when VIEW intent with application pdf type and flag disabled then not treated as local PDF`() = runTest {
        val contentUri = Uri.parse("content://com.example.provider/files/doc.pdf")
        val disabledToggle = mockToggle(enabled = false)
        whenever(mockIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(mockIntent.data).thenReturn(contentUri)
        whenever(mockIntent.type).thenReturn("application/pdf")
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(disabledToggle)

        testee.onIntentReceived(mockIntent, isExternal = true)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.openLocalPdf)
        }
        verify(inlinePdfHandler, never()).cacheLocalPdf(any())
    }

    @Test
    fun `when ordinary https VIEW intent received then openLocalPdf is false and normal flow proceeds`() = runTest {
        val url = "https://example.com"
        whenever(mockIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(mockIntent.data).thenReturn(Uri.parse(url))
        whenever(mockIntent.type).thenReturn(null)
        configureHasSession(false)

        testee.onIntentReceived(mockIntent, isExternal = false)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.openLocalPdf)
            assertEquals(url, state.intentText)
        }
    }

    private fun configureHasSession(returnValue: Boolean) {
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)).thenReturn(returnValue)
    }

    private fun configureIsEmailProtectionLink(returnValue: Boolean) {
        whenever(emailProtectionLinkVerifier.shouldDelegateToInContextView(anyOrNull(), any())).thenReturn(returnValue)
    }

    private fun mockToggle(enabled: Boolean): Toggle {
        val toggle: Toggle = mock()
        whenever(toggle.isEnabled()).thenReturn(enabled)
        return toggle
    }

    private companion object {
        private const val DEFAULT_COLOR = 0
    }
}
