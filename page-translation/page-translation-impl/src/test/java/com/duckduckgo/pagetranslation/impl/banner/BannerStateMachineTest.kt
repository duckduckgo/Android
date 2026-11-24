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

import com.duckduckgo.pagetranslation.impl.banner.BannerEvent.Dismiss
import com.duckduckgo.pagetranslation.impl.banner.BannerEvent.OfferChanged
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerStateMachineTest {

    private val first = ActiveOffer(LanguagePair("es", "en"), document = 1)
    private val second = ActiveOffer(LanguagePair("es", "en"), document = 2)
    private val noOffer = BannerState.Hidden(HiddenReason.NoOffer())

    private fun reduce(state: BannerState, vararg events: BannerEvent) = events.fold(state) { s, e -> BannerStateMachine.reduce(s, e) }

    @Test
    fun whenOfferArrivesThenShowing() {
        assertEquals(BannerState.Showing(first), reduce(noOffer, OfferChanged(first)))
    }

    @Test
    fun whenOfferGoesAwayWhileShowingThenHiddenForNoOffer() {
        assertEquals(BannerState.Hidden(HiddenReason.NoOffer(lastShown = 1)), reduce(noOffer, OfferChanged(first), OfferChanged(null)))
    }

    @Test
    fun whenDismissedThenHiddenWithDocumentAndReason() {
        Dismissal.entries.forEach { by ->
            assertEquals(
                BannerState.Hidden(HiddenReason.Dismissed(1, by)),
                reduce(noOffer, OfferChanged(first), Dismiss(by)),
            )
        }
    }

    @Test
    fun whenDismissedAndSameOfferIsEmittedAgainThenStaysHidden() {
        val dismissed = reduce(noOffer, OfferChanged(first), Dismiss(Dismissal.Swipe))

        assertEquals(dismissed, reduce(dismissed, OfferChanged(first)))
    }

    @Test
    fun whenDismissedAndOfferGoesAwayAndComesBackForSameDocumentThenStaysHidden() {
        val dismissed = reduce(noOffer, OfferChanged(first), Dismiss(Dismissal.Timeout))

        assertEquals(dismissed, reduce(dismissed, OfferChanged(null), OfferChanged(first)))
    }

    @Test
    fun whenShowingThenOfferGoesAwayAndSameDocumentComesBackThenStaysHidden() {
        val hidden = reduce(noOffer, OfferChanged(first), OfferChanged(null))

        assertEquals(hidden, reduce(hidden, OfferChanged(first)))
    }

    @Test
    fun whenShowingThenOfferGoesAwayAndNewDocumentArrivesThenShowingAgain() {
        val hidden = reduce(noOffer, OfferChanged(first), OfferChanged(null))

        assertEquals(BannerState.Showing(second), reduce(hidden, OfferChanged(second)))
    }

    @Test
    fun whenDismissedAndNewDocumentArrivesThenShowingAgain() {
        val dismissed = reduce(noOffer, OfferChanged(first), Dismiss(Dismissal.Swipe))

        assertEquals(BannerState.Showing(second), reduce(dismissed, OfferChanged(second)))
    }

    @Test
    fun whenSameOfferIsEmittedWhileShowingThenUnchanged() {
        val showing = BannerState.Showing(first)

        assertEquals(showing, reduce(showing, OfferChanged(first)))
    }

    @Test
    fun whenDismissWithoutBannerThenUnchanged() {
        assertEquals(noOffer, reduce(noOffer, Dismiss(Dismissal.Timeout)))
    }

    @Test
    fun whenDismissedTwiceThenFirstReasonKept() {
        assertEquals(
            BannerState.Hidden(HiddenReason.Dismissed(1, Dismissal.Swipe)),
            reduce(noOffer, OfferChanged(first), Dismiss(Dismissal.Swipe), Dismiss(Dismissal.Timeout)),
        )
    }
}
