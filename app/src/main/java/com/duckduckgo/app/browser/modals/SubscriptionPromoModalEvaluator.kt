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
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

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

        // Paced and resolved here rather than inside the show action: both can still decline, and a
        // claim held while deciding refuses a competing prompt that would otherwise have its turn.
        //
        // The delay stays ahead of the lookup: the process resumes from Activity.onResume, while the
        // tab registers its presenter when fragments resume afterwards, so looking first would race
        // registration and skip the promo on resume.
        delay(MODAL_DISPLAY_DELAY)
        val presenter = presenterRegistry.current()
        if (presenter == null) {
            logcat { "SubscriptionPromoModalEvaluator: skipped, no presenter registered" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        ModalEvaluator.EvaluationResult.WantsToShow {
            presenter.showSubscriptionPromo(decision.flow, decision.isFreeTrialCopy)
        }
    }

    companion object {
        private const val PRIORITY = 5
        private const val MODAL_DISPLAY_DELAY = 250L
    }
}
