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

package com.duckduckgo.app.onboarding.ui.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [OnboardingStepState] — the pure Kotlin state holder that backs
 * [OnboardingStepIndicatorView].
 */
class OnboardingStepIndicatorViewTest {

    private lateinit var state: OnboardingStepState

    @Before
    fun setup() {
        state = OnboardingStepState()
    }

    @Test
    fun `setSteps configures total steps and current step`() {
        state.setSteps(totalSteps = 5, currentStep = 1)

        assertEquals(5, state.totalSteps)
        assertEquals(1, state.currentStep)
    }

    @Test
    fun `setSteps defaults current step to 1`() {
        state.setSteps(totalSteps = 3)

        assertEquals(3, state.totalSteps)
        assertEquals(1, state.currentStep)
    }

    @Test
    fun `setCurrentStep updates current step`() {
        state.setSteps(totalSteps = 5, currentStep = 1)

        state.setCurrentStep(3)

        assertEquals(3, state.currentStep)
    }

    @Test
    fun `setCurrentStep clamps below min to 1`() {
        state.setSteps(totalSteps = 3, currentStep = 1)

        state.setCurrentStep(0)

        assertEquals(1, state.currentStep)
    }

    @Test
    fun `setCurrentStep clamps above max to totalSteps`() {
        state.setSteps(totalSteps = 3, currentStep = 1)

        state.setCurrentStep(5)

        assertEquals(3, state.currentStep)
    }

    @Test
    fun `hasNextStep is true when not at last step`() {
        state.setSteps(totalSteps = 3, currentStep = 1)

        assertTrue(state.hasNextStep)
    }

    @Test
    fun `hasNextStep is false when at last step`() {
        state.setSteps(totalSteps = 3, currentStep = 3)

        assertFalse(state.hasNextStep)
    }

    @Test
    fun `advanceToNextStep increments current step`() {
        state.setSteps(totalSteps = 5, currentStep = 1)

        state.advanceToNextStep()

        assertEquals(2, state.currentStep)
    }

    @Test
    fun `advanceToNextStep does nothing when at last step`() {
        state.setSteps(totalSteps = 3, currentStep = 3)

        state.advanceToNextStep()

        assertEquals(3, state.currentStep)
    }

    @Test
    fun `activeStepIndex is zero-based`() {
        state.setSteps(totalSteps = 3, currentStep = 2)

        assertEquals(1, state.activeStepIndex)
    }
}
