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
import com.duckduckgo.app.cta.ui.SubscriptionPromoModalDecider
import com.duckduckgo.app.cta.ui.SubscriptionPromoModalDecision
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

class SubscriptionPromoModalEvaluatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val decider: SubscriptionPromoModalDecider = mock()
    private val registry = NewTabPageModalPresenterRegistry()

    private lateinit var testee: SubscriptionPromoModalEvaluator

    @Before
    fun before() {
        testee = SubscriptionPromoModalEvaluator(decider, registry, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun triggerIsAppResume() {
        assertEquals(ModalTrigger.APP_RESUME, testee.trigger)
    }

    @Test
    fun whenNotEligibleThenSkipped() = runTest {
        whenever(decider.decide()).thenReturn(null)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleButNoPresenterRegisteredThenSkippedWithoutClaiming() = runTest {
        whenever(decider.decide()).thenReturn(SubscriptionPromoModalDecision(SubscriptionPromoFlow.NUDGE, isFreeTrialCopy = false))

        // Resolved during evaluation, so a missing host never costs a claim.
        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
    }

    @Test
    fun whenEligibleAndPresenterShowsThenModalShown() = runTest {
        whenever(decider.decide()).thenReturn(SubscriptionPromoModalDecision(SubscriptionPromoFlow.SKIPPED_ONBOARDING, isFreeTrialCopy = true))
        val presenter = FakePresenter(subscriptionResult = true)
        registry.register(presenter)

        val result = testee.evaluate()

        // Eligibility alone never shows: the modal only shows once the deferred action is invoked.
        assertFalse(presenter.subscriptionShown)
        assertTrue(result.invokeShow())
        assertTrue(presenter.subscriptionShown)
        assertEquals(SubscriptionPromoFlow.SKIPPED_ONBOARDING, presenter.shownFlow)
        assertTrue(presenter.shownFreeTrialCopy)
    }

    @Test
    fun whenEligibleButPresenterDeclinesThenShowFallsThrough() = runTest {
        whenever(decider.decide()).thenReturn(SubscriptionPromoModalDecision(SubscriptionPromoFlow.NUDGE, isFreeTrialCopy = false))
        registry.register(FakePresenter(subscriptionResult = false))

        assertFalse(testee.evaluate().invokeShow())
    }

    private suspend fun ModalEvaluator.EvaluationResult.invokeShow(): Boolean {
        assertTrue("expected WantsToShow but was $this", this is ModalEvaluator.EvaluationResult.WantsToShow)
        return (this as ModalEvaluator.EvaluationResult.WantsToShow).show()
    }

    private class FakePresenter(
        private val subscriptionResult: Boolean = true,
    ) : NewTabPageModalPresenter {
        var subscriptionShown = false
        var shownFlow: SubscriptionPromoFlow? = null
        var shownFreeTrialCopy = false

        override suspend fun showSubscriptionPromo(
            flow: SubscriptionPromoFlow,
            isFreeTrialCopy: Boolean,
        ): Boolean {
            subscriptionShown = true
            shownFlow = flow
            shownFreeTrialCopy = isFreeTrialCopy
            return subscriptionResult
        }

        override suspend fun showAddWidgetPromo(supportsAutomaticAdd: Boolean): Boolean = false
    }
}
