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
import com.duckduckgo.app.cta.ui.SubscriptionPromoFlow
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.modalcoordinator.api.ModalTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AddWidgetModalEvaluatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val widgetCapabilities: WidgetCapabilities = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val registry = NewTabPageModalPresenterRegistry()
    private val onboardingFlowChecker: OnboardingFlowChecker = mock()

    private lateinit var testee: AddWidgetModalEvaluator

    @Before
    fun before() = runTest {
        // Default: eligible (onboarding complete, no widgets installed, not dismissed).
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        testee = AddWidgetModalEvaluator(
            widgetCapabilities,
            dismissedCtaDao,
            registry,
            onboardingFlowChecker,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenOnboardingNotCompleteThenSkipped() = runTest {
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(false)
        registry.register(FakePresenter())

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun triggerIsNtpRender() {
        assertEquals(ModalTrigger.NTP_RENDER, testee.trigger)
    }

    @Test
    fun whenWidgetsAlreadyInstalledThenSkipped() = runTest {
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(true)
        registry.register(FakePresenter())

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenAddWidgetCtaDismissedThenSkipped() = runTest {
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(true)
        registry.register(FakePresenter())

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleButNoPresenterRegisteredThenSkipped() = runTest {
        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleAndPresenterShowsThenModalShownWithAutomaticAddFlag() = runTest {
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        val presenter = FakePresenter(widgetResult = true)
        registry.register(presenter)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        assertTrue(presenter.widgetShown)
        assertTrue(presenter.shownSupportsAutomaticAdd == true)
    }

    @Test
    fun whenEligibleButPresenterDeclinesThenSkipped() = runTest {
        registry.register(FakePresenter(widgetResult = false))

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    private class FakePresenter(
        private val widgetResult: Boolean = true,
    ) : NewTabPageModalPresenter {
        var widgetShown = false
        var shownSupportsAutomaticAdd: Boolean? = null

        override fun showSubscriptionPromo(
            flow: SubscriptionPromoFlow,
            isFreeTrialCopy: Boolean,
        ): Boolean = false

        override fun showAddWidgetPromo(supportsAutomaticAdd: Boolean): Boolean {
            widgetShown = true
            shownSupportsAutomaticAdd = supportsAutomaticAdd
            return widgetResult
        }
    }
}
