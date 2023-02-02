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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.feature.AutofillFeatureName.Autofill
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.AccessCredentialManagement
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.feature.toggles.api.FeatureToggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class AutofillGlobalCapabilityCheckerImplGlobalFeatureTest(
    private val testCase: TestCase,
) {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val featureToggle: FeatureToggle = mock()
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val autofillStore: AutofillStore = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val exceptionChecker: com.duckduckgo.autofill.api.Autofill = mock()

    private val testee = AutofillGlobalCapabilityCheckerImpl(
        featureToggle = featureToggle,
        internalTestUserChecker = internalTestUserChecker,
        autofillStore = autofillStore,
        deviceAuthenticator = deviceAuthenticator,
        autofill = exceptionChecker,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun runParameterizedTests() = runTest {
        configureAsInternalTester(testCase.scenario.isInternalTester)
        configureGlobalAutofillFeatureState(testCase.scenario.isRemotelyEnabled)
        configureIfUrlIsException(testCase.scenario.urlIsInExceptionList)

        assertEquals("${testCase.scenario}", testCase.expectFeatureEnabled, testee.isAutofillEnabledByConfiguration("example.com"))
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return listOf(
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        urlIsInExceptionList = false,
                        isInternalTester = false,
                        isRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        urlIsInExceptionList = false,
                        isInternalTester = false,
                        isRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        urlIsInExceptionList = false,
                        isInternalTester = true,
                        isRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        urlIsInExceptionList = false,
                        isInternalTester = true,
                        isRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        urlIsInExceptionList = true,
                        isInternalTester = false,
                        isRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        urlIsInExceptionList = true,
                        isInternalTester = false,
                        isRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        urlIsInExceptionList = true,
                        isInternalTester = true,
                        isRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        urlIsInExceptionList = true,
                        isInternalTester = true,
                        isRemotelyEnabled = true,
                    ),
                ),

            )
        }
    }

    private fun configureGlobalAutofillFeatureState(isEnabled: Boolean) {
        whenever(featureToggle.isFeatureEnabled(Autofill.value)).thenReturn(isEnabled)
        whenever(featureToggle.isFeatureEnabled(eq(Autofill.value), any())).thenReturn(isEnabled)
    }

    private fun configureIfUrlIsException(isException: Boolean) {
        whenever(exceptionChecker.isAnException(any())).thenReturn(isException)
    }

    private fun configureAsInternalTester(isInternal: Boolean) = whenever(internalTestUserChecker.isInternalTestUser).thenReturn(isInternal)

    data class TestCase(
        val subfeature: AutofillSubfeatureName = AccessCredentialManagement,
        val expectFeatureEnabled: Boolean,
        val scenario: Scenario,
    )

    data class Scenario(
        val isInternalTester: Boolean,
        val isRemotelyEnabled: Boolean,
        val urlIsInExceptionList: Boolean,
    )
}
