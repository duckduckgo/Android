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
import org.junit.Assert.assertEquals
import org.junit.Test

class RealTranslationMenuViewStateResolverTest {

    private val resolver = RealTranslationMenuViewStateResolver()

    private val pair = LanguagePair("es", "en")
    private val allOffers = TranslationOffer.entries
    private val offersWithRow = listOf(TranslationOffer.BannerAndMenuRow, TranslationOffer.MenuRowOnly)
    private val allStates = listOf(
        SessionState.Idle,
        SessionState.Downloading,
        SessionState.Translating,
        SessionState.Translated,
    )

    private fun viewState(
        state: SessionState,
        offer: TranslationOffer = TranslationOffer.BannerAndMenuRow,
        content: TabContent = TabContent.WebPage,
        pair: LanguagePair? = this.pair,
    ) = resolver.resolve(content, TabTranslationSnapshot(offer, pair, state))

    @Test
    fun whenContentIsNotAWebPageThenAlwaysHidden() {
        listOf(TabContent.Pdf, TabContent.Other).forEach { content ->
            for (offer in allOffers) for (state in allStates) {
                assertEquals("$content $offer $state", TranslationMenuViewState.Hidden, viewState(state, offer, content))
            }
        }
    }

    @Test
    fun whenIdleThenTranslateIsEnabledOnlyWhenTheOfferAllowsARow() {
        for (offer in offersWithRow) {
            assertEquals(offer.toString(), TranslationMenuViewState.Translate("en", enabled = true), viewState(SessionState.Idle, offer))
        }
        assertEquals(TranslationMenuViewState.Hidden, viewState(SessionState.Idle, TranslationOffer.None))
    }

    @Test
    fun whenDownloadingThenTranslateIsDisabled() {
        for (offer in offersWithRow) {
            assertEquals(offer.toString(), TranslationMenuViewState.Translate("en", enabled = false), viewState(SessionState.Downloading, offer))
        }
        assertEquals(TranslationMenuViewState.Hidden, viewState(SessionState.Downloading, TranslationOffer.None))
    }

    @Test
    fun whenTranslatingThenTranslatingForEveryOffer() {
        for (offer in allOffers) {
            assertEquals(offer.toString(), TranslationMenuViewState.InProgress, viewState(SessionState.Translating, offer))
        }
    }

    @Test
    fun whenTranslatedThenShowOriginalForEveryOffer() {
        for (offer in allOffers) {
            assertEquals(offer.toString(), TranslationMenuViewState.ShowOriginal, viewState(SessionState.Translated, offer))
        }
    }

    @Test
    fun whenThereIsNoPairThenTranslateIsHidden() {
        assertEquals(TranslationMenuViewState.Hidden, viewState(SessionState.Idle, pair = null))
        assertEquals(TranslationMenuViewState.Hidden, viewState(SessionState.Downloading, pair = null))
    }

    @Test
    fun whenTheTargetIsNotTheSourceThenTheRowNamesTheTarget() {
        assertEquals(TranslationMenuViewState.Translate("fr", enabled = true), viewState(SessionState.Idle, pair = LanguagePair("de", "fr")))
    }
}
