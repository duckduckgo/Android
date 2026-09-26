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
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val onboardingStore: OnboardingStore = mock()

    private lateinit var testee: AddWidgetModalEvaluator

    @Before
    fun before() = runTest {
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(onboardingStore.linearPlanWidgetPromptShown).thenReturn(false)

        testee = AddWidgetModalEvaluator(
            widgetCapabilities,
            dismissedCtaDao,
            registry,
            onboardingStore,
            coroutineRule.testDispatcherProvider,
        )
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
    fun whenLinearPlanWidgetPromptShownThenSkipped() = runTest {
        whenever(onboardingStore.linearPlanWidgetPromptShown).thenReturn(true)
        registry.register(FakePresenter())

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleButNoPresenterRegisteredThenSkippedWithoutClaiming() = runTest {
        // Resolved during evaluation, so a missing host never costs a claim.
        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleAndPresenterShowsThenModalShownWithAutomaticAddFlag() = runTest {
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        val presenter = FakePresenter(widgetResult = true)
        registry.register(presenter)

        val result = testee.evaluate()

        // Eligibility alone never shows: the modal only shows once the deferred action is invoked.
        assertFalse(presenter.widgetShown)
        assertTrue(result.invokeShow())
        assertTrue(presenter.widgetShown)
        assertTrue(presenter.shownSupportsAutomaticAdd == true)
    }

    @Test
    fun whenEligibleButPresenterDeclinesThenShowFallsThrough() = runTest {
        registry.register(FakePresenter(widgetResult = false))

        assertFalse(testee.evaluate().invokeShow())
    }

    private suspend fun ModalEvaluator.EvaluationResult.invokeShow(): Boolean {
        assertTrue("expected WantsToShow but was $this", this is ModalEvaluator.EvaluationResult.WantsToShow)
        return (this as ModalEvaluator.EvaluationResult.WantsToShow).show()
    }

    private class FakePresenter(
        private val widgetResult: Boolean = true,
    ) : NewTabPageModalPresenter {
        var widgetShown = false
        var shownSupportsAutomaticAdd: Boolean? = null

        override suspend fun showSubscriptionPromo(
            flow: SubscriptionPromoFlow,
            isFreeTrialCopy: Boolean,
        ): Boolean = false

        override suspend fun showAddWidgetPromo(supportsAutomaticAdd: Boolean): Boolean {
            widgetShown = true
            shownSupportsAutomaticAdd = supportsAutomaticAdd
            return widgetResult
        }
    }
}
