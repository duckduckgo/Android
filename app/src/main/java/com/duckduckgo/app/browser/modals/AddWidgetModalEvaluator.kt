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
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.modalcoordinator.api.ModalTrigger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Coordinates the "Add Widget" promo. Runs on [ModalTrigger.NTP_RENDER] because the promo is
 * render-bound to the empty New Tab Page (including mid-session new tabs that produce no app
 * foreground), so it cannot rely on [ModalTrigger.APP_RESUME] alone.
 *
 * The promo is a bottom sheet owned by the visible browser tab, so rendering is delegated to the
 * [NewTabPageModalPresenter] that tab registers.
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
    private val onboardingFlowChecker: OnboardingFlowChecker,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator {

    override val priority: Int = PRIORITY
    override val evaluatorId: String = "add_widget_modal"
    override val trigger: ModalTrigger = ModalTrigger.NTP_RENDER

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        // Onboarding CTAs own the NTP during onboarding (as they did when Add Widget lived in
        // getHomeCta); don't interrupt them.
        if (!onboardingFlowChecker.isOnboardingComplete()) {
            logcat { "AddWidgetModalEvaluator: skipped, onboarding not complete" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        if (!canShowWidgetCta()) {
            logcat {
                "AddWidgetModalEvaluator: skipped, not eligible " +
                    "(hasInstalledWidgets=${widgetCapabilities.hasInstalledWidgets}, " +
                    "dismissed=${dismissedCtaDao.exists(CtaId.ADD_WIDGET)})"
            }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        val presenter = presenterRegistry.current()
        if (presenter == null) {
            logcat { "AddWidgetModalEvaluator: skipped, no presenter registered" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        val shown = withContext(dispatchers.main()) {
            presenter.showAddWidgetPromo(widgetCapabilities.supportsAutomaticWidgetAdd)
        }
        if (shown) {
            ModalEvaluator.EvaluationResult.ModalShown
        } else {
            logcat { "AddWidgetModalEvaluator: skipped, presenter declined (not on New Tab Page)" }
            ModalEvaluator.EvaluationResult.Skipped
        }
    }

    private fun canShowWidgetCta(): Boolean =
        !widgetCapabilities.hasInstalledWidgets && !dismissedCtaDao.exists(CtaId.ADD_WIDGET)

    companion object {
        private const val PRIORITY = 5
    }
}
