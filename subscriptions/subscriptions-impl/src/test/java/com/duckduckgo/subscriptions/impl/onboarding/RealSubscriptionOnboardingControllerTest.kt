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

package com.duckduckgo.subscriptions.impl.onboarding

import app.cash.turbine.test
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController.Event
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealSubscriptionOnboardingControllerTest {

    private val testee = RealSubscriptionOnboardingController()

    @Test
    fun whenOnStepFinishedThenEmitsStepFinishedEvent() = runTest {
        testee.events.test {
            testee.onStepFinished("welcome", COMPLETED)

            assertEquals(Event.StepFinished("welcome", COMPLETED), awaitItem())
        }
    }

    @Test
    fun whenOnBackThenEmitsBackEvent() = runTest {
        testee.events.test {
            testee.onBack()

            assertEquals(Event.Back, awaitItem())
        }
    }

    @Test
    fun whenExitOnboardingThenEmitsExitEvent() = runTest {
        testee.events.test {
            testee.exitOnboarding()

            assertEquals(Event.Exit, awaitItem())
        }
    }
}
