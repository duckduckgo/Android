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

package com.duckduckgo.app.browser.modals

import com.duckduckgo.app.cta.ui.SubscriptionPromoFlow
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Renders coordinator-arbitrated New Tab Page promos as bottom sheets.
 *
 * The modal coordinator runs in [AppScope] and cannot attach a bottom sheet to a fragment, so the
 * currently visible browser tab registers an implementation of this interface with the
 * [NewTabPageModalPresenterRegistry] while it is on screen. Evaluators resolve the current presenter
 * and ask it to render; the presenter reuses the existing CTA render path so all pixels and dismiss
 * handling stay intact.
 *
 * All methods must be called on the main thread and return true only if the promo was accepted for
 * display on the current surface (e.g. the tab is on a valid screen). A false return tells the
 * evaluator the modal was not shown, so no cooldown is recorded.
 */
interface NewTabPageModalPresenter {

    /** Shows the Privacy Pro promo. Valid on the NTP or over a website (never over Duck.ai). */
    fun showSubscriptionPromo(
        flow: SubscriptionPromoFlow,
        isFreeTrialCopy: Boolean,
    ): Boolean

    /** Shows the Add Widget promo. Valid only on the New Tab Page. */
    fun showAddWidgetPromo(supportsAutomaticAdd: Boolean): Boolean
}

/**
 * Holds the presenter for the currently visible browser tab. "Last registered wins", so the most
 * recently visible tab is the active presenter; a tab only clears the presenter if it is still the
 * registered one, making stale unregister calls safe.
 */
@SingleInstanceIn(AppScope::class)
class NewTabPageModalPresenterRegistry @Inject constructor() {

    @Volatile
    private var presenter: NewTabPageModalPresenter? = null

    fun register(presenter: NewTabPageModalPresenter) {
        this.presenter = presenter
    }

    fun unregister(presenter: NewTabPageModalPresenter) {
        if (this.presenter === presenter) {
            this.presenter = null
        }
    }

    fun current(): NewTabPageModalPresenter? = presenter
}
