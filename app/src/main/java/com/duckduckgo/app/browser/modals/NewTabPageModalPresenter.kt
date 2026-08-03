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
 * Renders New Tab Page promos as bottom sheets. The coordinator runs in [AppScope] and cannot attach
 * one to a fragment, so the visible browser tab registers an implementation while it is on screen.
 *
 * Returning false means the promo was not shown, so no cooldown is recorded.
 */
interface NewTabPageModalPresenter {

    /** Shows the Privacy Pro promo. Valid on the NTP or over a website (never over Duck.ai). */
    suspend fun showSubscriptionPromo(
        flow: SubscriptionPromoFlow,
        isFreeTrialCopy: Boolean,
    ): Boolean

    /** Shows the Add Widget promo. Valid only on the New Tab Page. */
    suspend fun showAddWidgetPromo(supportsAutomaticAdd: Boolean): Boolean
}

/**
 * Holds the presenter for the visible browser tab: last registered wins, and a tab only clears the
 * presenter if it is still the registered one, so stale unregister calls are safe.
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
