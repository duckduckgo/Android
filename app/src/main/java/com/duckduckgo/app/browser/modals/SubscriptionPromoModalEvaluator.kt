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

import com.duckduckgo.app.cta.ui.SubscriptionPromoModalDecider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.modalcoordinator.api.ModalTrigger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Coordinates the Privacy Pro promo modal. Runs on [ModalTrigger.APP_RESUME] (app foreground),
 * replacing the previous `checkSubscriptionPromoOnForeground` path so the promo now respects the
 * coordinator's priority ordering and 24-hour cooldown alongside the other app-originated modals.
 *
 * The promo is a bottom sheet owned by the visible browser tab, so rendering is delegated to the
 * [NewTabPageModalPresenter] that tab registers.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class SubscriptionPromoModalEvaluator @Inject constructor(
    private val decider: SubscriptionPromoModalDecider,
    private val presenterRegistry: NewTabPageModalPresenterRegistry,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator {

    override val priority: Int = PRIORITY
    override val evaluatorId: String = "subscription_promo_modal"
    override val trigger: ModalTrigger = ModalTrigger.APP_RESUME

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        val decision = decider.decide()
            ?: return@withContext ModalEvaluator.EvaluationResult.Skipped

        // On foreground the coordinator can run before the browser tab has registered its presenter;
        // give it a brief moment (matching other evaluators' display delay) before resolving it.
        delay(MODAL_DISPLAY_DELAY)
        val presenter = presenterRegistry.current()
        if (presenter == null) {
            logcat { "SubscriptionPromoModalEvaluator: no presenter registered, skipping" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        val shown = withContext(dispatchers.main()) {
            presenter.showSubscriptionPromo(decision.flow, decision.isFreeTrialCopy)
        }
        if (shown) {
            ModalEvaluator.EvaluationResult.ModalShown
        } else {
            ModalEvaluator.EvaluationResult.Skipped
        }
    }

    companion object {
        private const val PRIORITY = 5
        private const val MODAL_DISPLAY_DELAY = 250L
    }
}
