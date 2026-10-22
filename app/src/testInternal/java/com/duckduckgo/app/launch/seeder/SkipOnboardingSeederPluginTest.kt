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

package com.duckduckgo.app.launch.seeder

import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SkipOnboardingSeederPluginTest {

    private val onboardingSkipper: OnboardingSkipper = mock()
    private val plugin = SkipOnboardingSeederPlugin(onboardingSkipper)

    @Test
    fun whenValueIsTrueThenOnboardingIsMarkedCompleted() = runTest {
        plugin.apply(key = "skipOnboarding", value = "true")

        verify(onboardingSkipper).markOnboardingAsCompleted()
    }

    @Test
    fun whenValueIsFalseThenOnboardingIsLeftUntouched() = runTest {
        plugin.apply(key = "skipOnboarding", value = "false")

        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }

    @Test
    fun whenValueIsUnknownThenApplyThrows() = runTest {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.apply(key = "skipOnboarding", value = "maybe") }
        }
        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }
}
