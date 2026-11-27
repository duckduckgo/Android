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

package com.duckduckgo.pagetranslation.impl.banner

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDirectories
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownload
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.RealTranslateRequest
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BannerControllerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pair = LanguagePair("es", "en")
    private val first = ActiveOffer(pair, document = 1)
    private val second = ActiveOffer(pair, document = 2)
    private val offers = MutableStateFlow<ActiveOffer?>(null)
    private val offerSource: TranslationOfferSource = mock { on { offer } doReturn offers }
    private val directories: LanguagePackDirectories = mock()
    private val translateAction: TranslateAction = mock()
    private val downloadPrompt: LanguagePackDownloadPrompt = mock()
    private val languagePackDownload: LanguagePackDownload = mock()
    private val testee =
        BannerController(
            offerSource,
            RealTranslateRequest(directories, translateAction, downloadPrompt, languagePackDownload, coroutineRule.testDispatcherProvider),
            TIMEOUT,
        )

    private fun TestScope.showFirst() {
        start()
        offers.value = first
        runCurrent()
    }

    private fun TestScope.start(onScreen: Boolean = true) {
        backgroundScope.launch { testee.run() }
        testee.setOnScreen(onScreen)
        runCurrent()
    }

    private val showing get() = testee.state.value is BannerState.Showing

    @Test
    fun whenOfferArrivesThenShowing() = runTest {
        start()
        offers.value = first
        runCurrent()

        assertEquals(BannerState.Showing(first), testee.state.value)
    }

    @Test
    fun whenNoOfferThenHidden() = runTest {
        start()

        assertEquals(BannerState.Hidden(HiddenReason.NoOffer()), testee.state.value)
    }

    @Test
    fun whenTimeoutElapsesThenDismissed() = runTest {
        start()
        offers.value = first
        runCurrent()

        advanceTimeBy(TIMEOUT - 1)
        assertEquals(true, showing)
        advanceTimeBy(2)

        assertEquals(BannerState.Hidden(HiddenReason.Dismissed(1, Dismissal.Timeout)), testee.state.value)
    }

    @Test
    fun whenNotOnScreenThenTimerDoesNotRun() = runTest {
        start(onScreen = false)
        offers.value = first
        runCurrent()

        advanceTimeBy(TIMEOUT * 3)

        assertEquals(true, showing)
    }

    @Test
    fun whenBackOnScreenThenTimerStartsAgainInFull() = runTest {
        start()
        offers.value = first
        runCurrent()
        advanceTimeBy(TIMEOUT - 1_000)
        testee.setOnScreen(false)
        runCurrent()
        advanceTimeBy(TIMEOUT * 2)

        testee.setOnScreen(true)
        runCurrent()
        advanceTimeBy(TIMEOUT - 1)
        assertEquals(true, showing)
        advanceTimeBy(2)

        assertEquals(false, showing)
    }

    @Test
    fun whenInteractingThenTimerDoesNotDismiss() = runTest {
        showFirst()

        testee.setInteracting(true)
        runCurrent()
        advanceTimeBy(TIMEOUT * 3)

        assertEquals(true, showing)
    }

    @Test
    fun whenInteractionEndsThenFullTimeoutRestarts() = runTest {
        showFirst()
        advanceTimeBy(TIMEOUT - 1_000)
        testee.setInteracting(true)
        runCurrent()
        advanceTimeBy(TIMEOUT * 2)

        testee.setInteracting(false)
        runCurrent()
        advanceTimeBy(TIMEOUT - 1)
        assertEquals(true, showing)
        advanceTimeBy(2)

        assertEquals(false, showing)
    }

    @Test
    fun whenTimeoutIsInfiniteThenNeverDismissed() = runTest {
        val noAutoDismiss =
            BannerController(
                offerSource,
                RealTranslateRequest(directories, translateAction, downloadPrompt, languagePackDownload, coroutineRule.testDispatcherProvider),
                Long.MAX_VALUE,
            )
        backgroundScope.launch { noAutoDismiss.run() }
        noAutoDismiss.setOnScreen(true)
        offers.value = first
        runCurrent()

        advanceTimeBy(TIMEOUT * 1_000)

        assertEquals(BannerState.Showing(first), noAutoDismiss.state.value)
    }

    @Test
    fun whenNewDocumentArrivesThenTimerRestartsAndBannerShowsAgain() = runTest {
        start()
        offers.value = first
        runCurrent()
        testee.onDismiss(Dismissal.Swipe)

        offers.value = second
        runCurrent()
        advanceTimeBy(TIMEOUT - 1)

        assertEquals(BannerState.Showing(second), testee.state.value)
    }

    @Test
    fun whenSwipedThenSameDocumentIsNotShownAgain() = runTest {
        start()
        offers.value = first
        runCurrent()

        testee.onDismiss(Dismissal.Swipe)
        offers.value = first.copy()
        runCurrent()

        assertEquals(false, showing)
    }

    @Test
    fun whenOfferGoesAwayThenTimerIsCancelledAndHidden() = runTest {
        start()
        offers.value = first
        runCurrent()

        offers.value = null
        runCurrent()
        advanceTimeBy(TIMEOUT * 2)

        assertEquals(BannerState.Hidden(HiddenReason.NoOffer(lastShown = 1)), testee.state.value)
    }

    @Test
    fun whenTranslateTappedAndPackInstalledThenTranslates() = runTest {
        whenever(directories.getInstalledPairs()).thenReturn(setOf(pair.key))
        start()
        offers.value = first
        runCurrent()

        testee.onTranslate()

        verify(translateAction).translate(pair)
        verifyNoInteractions(downloadPrompt)
    }

    @Test
    fun whenTranslateTappedAndPackMissingThenRequestsDownload() = runTest {
        whenever(directories.getInstalledPairs()).thenReturn(emptySet())
        start()
        offers.value = first
        runCurrent()

        testee.onTranslate()

        verify(downloadPrompt).requestDownload(pair)
        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenTranslateTappedThenBannerIsDismissed() = runTest {
        whenever(directories.getInstalledPairs()).thenReturn(setOf(pair.key))
        start()
        offers.value = first
        runCurrent()

        testee.onTranslate()

        assertEquals(BannerState.Hidden(HiddenReason.Dismissed(1, Dismissal.Translate)), testee.state.value)
    }

    @Test
    fun whenTranslateTappedWithoutBannerThenNothingHappens() = runTest {
        start()

        testee.onTranslate()

        verifyNoInteractions(translateAction, downloadPrompt)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
