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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.BookmarksImportParams
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin.Companion.PRIORITY_KEY_IMPORT_PASSWORDS_PROMO
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin.Companion.PRIORITY_KEY_SYNC_PROMO
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.Command.Exit
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.ViewMode
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.DownloadError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.Unknown
import com.duckduckgo.autofill.impl.importing.takeout.webflow.journey.ImportGoogleBookmarksJourney
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.time.Duration.Companion.milliseconds

class ImportGoogleBookmarksViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockImportJourney: ImportGoogleBookmarksJourney = mock()

    private val promotionPlugins = mutableMapOf<Int, BookmarksImportScreenPromotionPlugin>()

    private val testee = createViewModel(isRedesignEnabled = false)

    @Test
    fun `when created then journey started with launch source`() {
        verify(mockImportJourney).started("bookmarks_screen")
    }

    @Test
    fun `when created then web flow loading shown`() {
        assertEquals(ViewMode.WebFlowLoading, testee.viewState.value.viewMode)
    }

    @Test
    fun `when waiting for export stopped while web flow loading then web flow shown`() {
        testee.onWaitingForExportStopped()

        assertEquals(ViewMode.WebFlow, testee.viewState.value.viewMode)
    }

    @Test
    fun `when waiting for export started then waiting for export shown`() {
        testee.onWaitingForExportStarted()

        assertEquals(ViewMode.WaitingForExport, testee.viewState.value.viewMode)
        verify(mockImportJourney).startedWaitingForExport()
    }

    @Test
    fun `when waiting for export stopped then web flow shown`() {
        testee.onWaitingForExportStarted()

        testee.onWaitingForExportStopped()

        assertEquals(ViewMode.WebFlow, testee.viewState.value.viewMode)
        verify(mockImportJourney).stoppedWaitingForExport()
    }

    @Test
    fun `when web flow finished with success then success shown`() {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10), testee.viewState.value.viewMode)
        verify(mockImportJourney).finishedWithSuccess()
    }

    @Test
    fun `when web flow finished with success and redesign enabled then waiting kept until promo decided`() {
        val decision = CompletableDeferred<Boolean>()
        val plugin = FakePromotionPlugin { decision.await() }
        promotionPlugins[1] = plugin
        val testee = createViewModel(isRedesignEnabled = true)
        testee.onWaitingForExportStarted()

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.WaitingForExport, testee.viewState.value.viewMode)
        assertEquals(listOf(BookmarksImportScreenPromotionPlugin.ImportResult(successCount = 10)), plugin.requests)

        decision.complete(true)

        assertEquals(ViewMode.Success(importedCount = 10, promo = plugin), testee.viewState.value.viewMode)
    }

    @Test
    fun `when several promos eligible then first eligible one in success`() {
        val skipped = FakePromotionPlugin { false }
        val first = FakePromotionPlugin { true }
        val second = FakePromotionPlugin { true }
        promotionPlugins += mapOf(3 to second, 1 to skipped, 2 to first)
        val testee = createViewModel(isRedesignEnabled = true)

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10, promo = first), testee.viewState.value.viewMode)
        assertTrue(second.requests.isEmpty())
    }

    @Test
    fun `when no promo eligible then success shown without promo`() {
        promotionPlugins[1] = FakePromotionPlugin { false }
        val testee = createViewModel(isRedesignEnabled = true)

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10, promo = null), testee.viewState.value.viewMode)
    }

    @Test
    fun `when promo not decided in time then success shown without promo`() = runTest {
        promotionPlugins[1] = FakePromotionPlugin { awaitCancellation() }
        val testee = createViewModel(isRedesignEnabled = true)
        testee.onWaitingForExportStarted()
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))
        assertEquals(ViewMode.WaitingForExport, testee.viewState.value.viewMode)

        advanceTimeBy(ImportGoogleBookmarksViewModel.PROMO_TIMEOUT + 1.milliseconds)

        assertEquals(ViewMode.Success(importedCount = 10, promo = null), testee.viewState.value.viewMode)
    }

    @Test
    fun `when web flow finished with success and redesign disabled then promos not asked`() {
        val plugin = FakePromotionPlugin { true }
        promotionPlugins[1] = plugin

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10, promo = null), testee.viewState.value.viewMode)
        assertTrue(plugin.requests.isEmpty())
    }

    @Test
    fun `when passwords import not offered then passwords import promo not shown`() {
        val fallback = FakePromotionPlugin { true }
        promotionPlugins += mapOf(PRIORITY_KEY_IMPORT_PASSWORDS_PROMO to FakePromotionPlugin { true }, PRIORITY_KEY_SYNC_PROMO to fallback)
        val testee = createViewModel(isRedesignEnabled = true, offerPasswordsImport = false)

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10, promo = fallback), testee.viewState.value.viewMode)
    }

    @Test
    fun `when passwords import offered then passwords import promo shown`() {
        val passwordsImportPromo = FakePromotionPlugin { true }
        promotionPlugins += mapOf(
            PRIORITY_KEY_IMPORT_PASSWORDS_PROMO to passwordsImportPromo,
            PRIORITY_KEY_SYNC_PROMO to FakePromotionPlugin { true },
        )
        val testee = createViewModel(isRedesignEnabled = true)

        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        assertEquals(ViewMode.Success(importedCount = 10, promo = passwordsImportPromo), testee.viewState.value.viewMode)
    }

    @Test
    fun `when web flow finished with error then error shown`() {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Error(DownloadError))

        assertEquals(ViewMode.Error(DownloadError), testee.viewState.value.viewMode)
        verify(mockImportJourney).finishedWithError(DownloadError)
    }

    @Test
    fun `when web flow finished without result then unknown error shown without reporting journey end`() {
        testee.onWebFlowFinished(null)

        assertEquals(ViewMode.Error(Unknown), testee.viewState.value.viewMode)
        verify(mockImportJourney, never()).finishedWithSuccess()
        verify(mockImportJourney, never()).finishedWithError(any())
        verify(mockImportJourney, never()).cancelled(any())
    }

    @Test
    fun `when web flow cancelled then exit with cancellation`() = runTest {
        testee.commands.test {
            testee.onWebFlowFinished(ImportGoogleBookmarkResult.UserCancelled(stage = "login"))

            assertEquals(Exit(ImportGoogleBookmarkResult.UserCancelled(stage = "login")), awaitItem())
        }
        verify(mockImportJourney).cancelled("login")
    }

    @Test
    fun `when waiting for export started after success then success still shown`() {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        testee.onWaitingForExportStarted()

        assertEquals(ViewMode.Success(importedCount = 10), testee.viewState.value.viewMode)
    }

    @Test
    fun `when waiting for export stopped after error then error still shown`() {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Error(DownloadError))

        testee.onWaitingForExportStopped()

        assertEquals(ViewMode.Error(DownloadError), testee.viewState.value.viewMode)
    }

    @Test
    fun `when result screen done clicked after success then exit with success`() = runTest {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        testee.commands.test {
            testee.onResultScreenDoneClicked()

            assertEquals(Exit(ImportGoogleBookmarkResult.Success(importedCount = 10)), awaitItem())
        }
    }

    @Test
    fun `when result screen done clicked after error then exit with error`() = runTest {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Error(DownloadError))

        testee.commands.test {
            testee.onResultScreenDoneClicked()

            assertEquals(Exit(ImportGoogleBookmarkResult.Error(DownloadError)), awaitItem())
        }
    }

    @Test
    fun `when result screen done clicked during web flow loading then no exit`() = runTest {
        testee.commands.test {
            testee.onResultScreenDoneClicked()

            expectNoEvents()
        }
    }

    @Test
    fun `when result screen done clicked during web flow then no exit`() = runTest {
        testee.onWaitingForExportStopped()

        testee.commands.test {
            testee.onResultScreenDoneClicked()

            expectNoEvents()
        }
    }

    @Test
    fun `when back pressed after success then exit with success`() = runTest {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Success(importedCount = 10))

        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Exit(ImportGoogleBookmarkResult.Success(importedCount = 10)), awaitItem())
        }
    }

    @Test
    fun `when back pressed after error then exit with error`() = runTest {
        testee.onWebFlowFinished(ImportGoogleBookmarkResult.Error(DownloadError))

        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Exit(ImportGoogleBookmarkResult.Error(DownloadError)), awaitItem())
        }
    }

    @Test
    fun `when back pressed during web flow loading then exit as cancelled`() = runTest {
        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Exit(ImportGoogleBookmarkResult.UserCancelled(stage = "uninitialized")), awaitItem())
        }
        verify(mockImportJourney).cancelled("uninitialized")
    }

    @Test
    fun `when back pressed while waiting for export then exit as cancelled`() = runTest {
        testee.onWaitingForExportStarted()

        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Exit(ImportGoogleBookmarkResult.UserCancelled(stage = "uninitialized")), awaitItem())
        }
    }

    private fun createViewModel(
        isRedesignEnabled: Boolean,
        offerPasswordsImport: Boolean = true,
    ) = ImportGoogleBookmarksViewModel(
        params = BookmarksImportParams(launchSource = "bookmarks_screen", offerPasswordsImport = offerPasswordsImport),
        isRedesignEnabled = isRedesignEnabled,
        importJourney = mockImportJourney,
        promotionPlugins = promotionPlugins,
    )

    private class FakePromotionPlugin(private val eligible: suspend () -> Boolean) : BookmarksImportScreenPromotionPlugin {
        val requests = mutableListOf<BookmarksImportScreenPromotionPlugin.ImportResult>()

        override suspend fun isEligible(data: BookmarksImportScreenPromotionPlugin.ImportResult): Boolean {
            requests += data
            return eligible()
        }

        override fun createView(
            context: Context,
            callback: BookmarksImportScreenPromotionPlugin.Callback,
        ): View = error("the ViewModel never builds the card")
    }
}
