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

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.widget.ui.WidgetCapabilities
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

/**
 * Coordinates the "Add Widget" promo. Uses [ModalTrigger.NTP_RENDER] because mid-session new tabs
 * render the NTP without foregrounding the app.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class AddWidgetModalEvaluator @Inject constructor(
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val presenterRegistry: NewTabPageModalPresenterRegistry,
    private val onboardingStore: OnboardingStore,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator {

    override val priority: Int = PRIORITY
    override val evaluatorId: String = "add_widget_modal"
    override val trigger: ModalTrigger = ModalTrigger.NTP_RENDER

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (!canShowWidgetCta()) {
            logcat {
                "AddWidgetModalEvaluator: skipped, not eligible " +
                    "(hasInstalledWidgets=${widgetCapabilities.hasInstalledWidgets}, " +
                    "dismissed=${dismissedCtaDao.exists(CtaId.ADD_WIDGET)}, " +
                    "linearPlanWidgetPromptShown=${onboardingStore.linearPlanWidgetPromptShown})"
            }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }
        delay(MODAL_DISPLAY_DELAY)
        val presenter = presenterRegistry.current()
        if (presenter == null) {
            logcat { "AddWidgetModalEvaluator: skipped, no presenter registered" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }
        val shown = presenter.showAddWidgetPromo(widgetCapabilities.supportsAutomaticWidgetAdd)
        if (shown) {
            ModalEvaluator.EvaluationResult.ModalShown
        } else {
            logcat { "AddWidgetModalEvaluator: skipped, presenter declined (not on New Tab Page)" }
            ModalEvaluator.EvaluationResult.Skipped
        }
    }

    private fun canShowWidgetCta(): Boolean =
        !widgetCapabilities.hasInstalledWidgets &&
            !dismissedCtaDao.exists(CtaId.ADD_WIDGET) &&
            !onboardingStore.linearPlanWidgetPromptShown

    companion object {
        private const val PRIORITY = 5
        private const val MODAL_DISPLAY_DELAY = 250L
    }
}
