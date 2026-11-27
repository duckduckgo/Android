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

package com.duckduckgo.pagetranslation.impl.menu

import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.offer.TranslationOffer
import com.duckduckgo.pagetranslation.impl.translate.TranslateRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleTabHolderTest {

    private val pair = LanguagePair("es", "en")
    private val holder = RealVisibleTabHolder(TestScope(UnconfinedTestDispatcher()))

    private class FakeSource(
        offer: TranslationOffer = TranslationOffer.BannerAndMenuRow,
        pair: LanguagePair? = LanguagePair("es", "en"),
        state: SessionState = SessionState.Idle,
    ) : TranslationMenuSource {
        override val menuOffer = MutableStateFlow(offer)
        override val pair = MutableStateFlow(pair)
        override val state = MutableStateFlow(state)
        var showOriginalCalls = 0
        override fun showOriginal() {
            showOriginalCalls++
        }
    }

    private val translateRequest: TranslateRequest = mock()

    private fun tab(source: FakeSource = FakeSource()) = TabMenu(source, translateRequest, holder, RealTranslationMenuViewStateResolver())

    private val translate = TranslationMenuViewState.Translate("en", enabled = true)

    @Test
    fun whenNoTabIsVisibleThenHidden() {
        assertEquals(TranslationMenuViewState.Hidden, holder.viewState.value)
    }

    @Test
    fun whenTabBecomesVisibleThenItsRowIsExposed() {
        tab().onTabStateChanged(isVisible = true, TabContent.WebPage)

        assertEquals(translate, holder.viewState.value)
    }

    @Test
    fun whenVisibleTabsSessionChangesThenRowFollows() {
        val source = FakeSource()
        tab(source).onTabStateChanged(isVisible = true, TabContent.WebPage)

        source.state.value = SessionState.Translated

        assertEquals(TranslationMenuViewState.ShowOriginal, holder.viewState.value)
    }

    @Test
    fun whenTabHidesThenHidden() {
        val tab = tab()
        tab.onTabStateChanged(isVisible = true, TabContent.WebPage)
        tab.onTabStateChanged(isVisible = false, TabContent.WebPage)

        assertEquals(TranslationMenuViewState.Hidden, holder.viewState.value)
    }

    @Test
    fun whenNewTabShowsBeforeOldTabHidesThenOldHideDoesNotClearIt() {
        val old = tab(FakeSource(state = SessionState.Translated))
        val new = tab()
        old.onTabStateChanged(isVisible = true, TabContent.WebPage)

        new.onTabStateChanged(isVisible = true, TabContent.WebPage)
        old.onTabStateChanged(isVisible = false, TabContent.WebPage)

        assertEquals(translate, holder.viewState.value)
    }

    @Test
    fun whenOldTabHidesBeforeNewTabShowsThenNewTabIsExposed() {
        val old = tab(FakeSource(state = SessionState.Translated))
        val new = tab()
        old.onTabStateChanged(isVisible = true, TabContent.WebPage)

        old.onTabStateChanged(isVisible = false, TabContent.WebPage)
        new.onTabStateChanged(isVisible = true, TabContent.WebPage)

        assertEquals(translate, holder.viewState.value)
    }

    @Test
    fun whenVisibleTabShowsAPdfThenHidden() {
        val tab = tab()
        tab.onTabStateChanged(isVisible = true, TabContent.WebPage)

        tab.onTabStateChanged(isVisible = true, TabContent.Pdf)

        assertEquals(TranslationMenuViewState.Hidden, holder.viewState.value)
    }

    @Test
    fun whenDetachedThenHiddenAndAttachingAgainRestoresAVisibleTab() {
        val tab = tab()
        tab.onTabStateChanged(isVisible = true, TabContent.WebPage)

        tab.onDetached()
        assertEquals(TranslationMenuViewState.Hidden, holder.viewState.value)

        tab.onAttached()
        assertEquals(translate, holder.viewState.value)
    }

    @Test
    fun whenAttachedWhileHiddenThenStaysHidden() {
        val tab = tab()
        tab.onTabStateChanged(isVisible = false, TabContent.WebPage)

        tab.onAttached()

        assertEquals(TranslationMenuViewState.Hidden, holder.viewState.value)
    }

    @Test
    fun whenRowTappedOnEnabledTranslateThenTranslatesThePair() = runTest {
        tab().onTabStateChanged(isVisible = true, TabContent.WebPage)

        holder.onRowTapped()

        verify(translateRequest).translate(pair)
    }

    @Test
    fun whenRowTappedWhileDownloadingThenNothingHappens() = runTest {
        tab(FakeSource(state = SessionState.Downloading)).onTabStateChanged(isVisible = true, TabContent.WebPage)

        holder.onRowTapped()

        verify(translateRequest, never()).translate(pair)
    }

    @Test
    fun whenRowTappedOnShowOriginalThenShowsOriginal() {
        val source = FakeSource(state = SessionState.Translated)
        tab(source).onTabStateChanged(isVisible = true, TabContent.WebPage)

        holder.onRowTapped()

        assertEquals(1, source.showOriginalCalls)
    }
}
