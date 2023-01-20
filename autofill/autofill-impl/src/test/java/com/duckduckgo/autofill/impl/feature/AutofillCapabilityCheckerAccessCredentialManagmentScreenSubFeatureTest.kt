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

package com.duckduckgo.autofill.impl.feature

import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.feature.AutofillFeatureName.Autofill
import com.duckduckgo.autofill.api.feature.AutofillFeatureToggle
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.impl.AutofillCapabilityCheckerImpl
import com.duckduckgo.autofill.impl.AutofillGlobalCapabilityChecker
import com.duckduckgo.feature.toggles.api.FeatureToggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class AutofillCapabilityCheckerAccessCredentialManagmentScreenSubFeatureTest(
    private val testCase: TestCase,
) {
    private val featureToggle: FeatureToggle = mock()
    private val autofillFeatureToggle: AutofillFeatureToggle = mock()
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker = mock()

    private val testee = AutofillCapabilityCheckerImpl(
        featureToggle = featureToggle,
        autofillFeatureToggle = autofillFeatureToggle,
        internalTestUserChecker = internalTestUserChecker,
        autofillGlobalCapabilityChecker = autofillGlobalCapabilityChecker,
    )

    @Test
    fun runParameterizedTests() = runTest {
        configureInternalTester(testCase.scenario.isInternalTester)
        configureGlobalAutofillCapability(testCase.scenario.isInternalTester, testCase.scenario.isAutofillGloballyEnabled)
        configureSubfeatureRemoteConfig(testCase.scenario.isSubfeatureRemotelyEnabled)

        assertEquals(
            String.format("Expected feature state wrong for scenario %s\n", testCase.scenario),
            testCase.expectFeatureEnabled,
            testee.canAccessCredentialManagementScreen(),
        )
    }

    companion object {

        private val subfeature = AutofillSubfeatureName.AccessCredentialManagement

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return listOf(
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = false,
                        isSubfeatureRemotelyEnabled = false,
                        isInternalTester = false,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = false,
                        isSubfeatureRemotelyEnabled = false,
                        isInternalTester = true,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = false,
                        isSubfeatureRemotelyEnabled = true,
                        isInternalTester = false,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = false,
                        isSubfeatureRemotelyEnabled = true,
                        isInternalTester = true,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = true,
                        isSubfeatureRemotelyEnabled = false,
                        isInternalTester = false,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = true,
                        isSubfeatureRemotelyEnabled = false,
                        isInternalTester = true,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = true,
                        isSubfeatureRemotelyEnabled = true,
                        isInternalTester = false,
                    ),
                ),
                TestCase(
                    subfeature = subfeature,
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isAutofillGloballyEnabled = true,
                        isSubfeatureRemotelyEnabled = true,
                        isInternalTester = true,
                    ),
                ),
            )
        }
    }

    private fun configureSubfeatureRemoteConfig(subfeatureRemotelyEnabled: Boolean) {
        whenever(autofillFeatureToggle.isFeatureEnabled(testCase.subfeature)).thenReturn(subfeatureRemotelyEnabled)
        whenever(autofillFeatureToggle.isFeatureEnabled(eq(testCase.subfeature), any())).thenReturn(subfeatureRemotelyEnabled)
    }

    private fun configureInternalTester(internalTester: Boolean) {
        whenever(internalTestUserChecker.isInternalTestUser).thenReturn(internalTester)
    }

    private fun configureGlobalAutofillCapability(
        isInternalTester: Boolean,
        isGloballyEnabled: Boolean,
    ) {
        val globallyEnabled = if (isInternalTester) {
            true
        } else {
            isGloballyEnabled
        }
        whenever(featureToggle.isFeatureEnabled(Autofill.value)).thenReturn(globallyEnabled)
        whenever(featureToggle.isFeatureEnabled(eq(Autofill.value), any())).thenReturn(globallyEnabled)
    }

    data class TestCase(
        val subfeature: AutofillSubfeatureName,
        val expectFeatureEnabled: Boolean,
        val scenario: Scenario,
    )

    data class Scenario(
        val isAutofillGloballyEnabled: Boolean,
        val isSubfeatureRemotelyEnabled: Boolean,
        val isInternalTester: Boolean,
    )
}
