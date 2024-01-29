/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl

import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class AutofillGlobalCapabilityCheckerImplUserPreferenceTest(
    private val testCase: TestCase,
) {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillFeature: AutofillFeature = mock()
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val autofill: Autofill = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()

    private val testee = AutofillGlobalCapabilityCheckerImpl(
        autofillFeature = autofillFeature,
        internalTestUserChecker = internalTestUserChecker,
        autofillStore = autofillStore,
        deviceAuthenticator = deviceAuthenticator,
        autofill = autofill,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun runParameterizedTests() = runTest {
        configureUserEnabledAutofill(testCase.scenario.isUserEnabled)

        assertEquals("${testCase.scenario}", testCase.expectFeatureEnabled, testee.isAutofillEnabledByUser())
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return listOf(
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isUserEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isUserEnabled = true,
                    ),
                ),
            )
        }
    }

    private fun configureUserEnabledAutofill(isEnabled: Boolean) {
        whenever(autofillStore.autofillEnabled).thenReturn(isEnabled)
    }

    data class TestCase(
        val expectFeatureEnabled: Boolean,
        val scenario: Scenario,
    )

    data class Scenario(
        val isUserEnabled: Boolean,
    )
}
