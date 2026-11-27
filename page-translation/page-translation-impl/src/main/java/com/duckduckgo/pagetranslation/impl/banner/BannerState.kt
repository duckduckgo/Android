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

import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer

sealed interface BannerState {
    data class Showing(val offer: ActiveOffer) : BannerState

    data class Hidden(val reason: HiddenReason) : BannerState
}

sealed interface HiddenReason {
    /**
     * No offer, or the source suppressed it. [lastShown] is the document of the banner that was showing, so the same document is not offered again.
     */
    data class NoOffer(val lastShown: Long? = null) : HiddenReason

    /**
     * Dismissed on the page identified by [document]. It stays dismissed until a new page load brings a new document.
     */
    data class Dismissed(val document: Long, val by: Dismissal) : HiddenReason
}

enum class Dismissal {
    Timeout,
    Swipe,
    AccessibilityAction,
    Translate,
}

sealed interface BannerEvent {
    data class OfferChanged(val offer: ActiveOffer?) : BannerEvent

    data class Dismiss(val by: Dismissal) : BannerEvent
}

object BannerStateMachine {

    fun reduce(
        state: BannerState,
        event: BannerEvent,
    ): BannerState = when (event) {
        is BannerEvent.OfferChanged -> onOfferChanged(state, event.offer)
        is BannerEvent.Dismiss -> onDismiss(state, event.by)
    }

    private fun onOfferChanged(
        state: BannerState,
        offer: ActiveOffer?,
    ): BannerState = when {
        offer == null -> if (state is BannerState.Showing) BannerState.Hidden(HiddenReason.NoOffer(state.offer.document)) else state
        state is BannerState.Hidden && state.reason.isAlreadyHandled(offer) -> state
        state is BannerState.Showing && state.offer == offer -> state
        else -> BannerState.Showing(offer)
    }

    private fun onDismiss(
        state: BannerState,
        by: Dismissal,
    ): BannerState = when (state) {
        is BannerState.Showing -> BannerState.Hidden(HiddenReason.Dismissed(state.offer.document, by))
        is BannerState.Hidden -> state
    }

    private fun HiddenReason.isAlreadyHandled(offer: ActiveOffer) = when (this) {
        is HiddenReason.Dismissed -> document == offer.document
        is HiddenReason.NoOffer -> lastShown == offer.document
    }
}
