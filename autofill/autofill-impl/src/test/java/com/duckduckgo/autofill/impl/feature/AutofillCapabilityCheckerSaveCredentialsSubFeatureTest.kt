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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.feature.AutofillFeatureToggle
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.SaveCredentials
import com.duckduckgo.autofill.impl.AutofillCapabilityCheckerImpl
import com.duckduckgo.autofill.impl.AutofillGlobalCapabilityChecker
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
class AutofillCapabilityCheckerSaveCredentialsSubFeatureTest(
    private val testCase: TestCase,
) {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val featureToggle: FeatureToggle = mock()
    private val autofillFeatureToggle: AutofillFeatureToggle = mock()
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker = mock()

    private val testee = AutofillCapabilityCheckerImpl(
        autofillFeatureToggle = autofillFeatureToggle,
        internalTestUserChecker = internalTestUserChecker,
        autofillGlobalCapabilityChecker = autofillGlobalCapabilityChecker,
        featureToggle = featureToggle,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun runParameterizedTests() = runTest {
        configureGlobalAutofillCapability(testCase.scenario.isInternalTester, testCase.scenario.isAutofillEnabledByConfig)
        configureInternalTester(testCase.scenario.isInternalTester)
        configureSecureStorageAvailable(testCase.scenario.isSecureStorageAvailable)
        configureSubfeatureRemoteConfig(testCase.scenario.isSubfeatureRemotelyEnabled)
        configureAutofillEnabledByUser(testCase.scenario.isAutofillEnabledByUser)

        assertEquals(
            String.format("Expected feature state wrong for scenario %s\n", testCase.scenario),
            testCase.expectFeatureEnabled,
            testee.canSaveCredentialsFromWebView("example.com"),
        )
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return listOf(
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = false,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),

                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = false,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = false,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = false,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = false,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = false,
                    ),
                ),
                TestCase(
                    expectFeatureEnabled = true,
                    scenario = Scenario(
                        isSecureStorageAvailable = true,
                        isAutofillEnabledByConfig = true,
                        isAutofillEnabledByUser = true,
                        isInternalTester = true,
                        isSubfeatureRemotelyEnabled = true,
                    ),
                ),
            )
        }
    }

    private fun configureSubfeatureRemoteConfig(subfeatureRemotelyEnabled: Boolean) {
        whenever(autofillFeatureToggle.isFeatureEnabled(testCase.subfeature.value)).thenReturn(subfeatureRemotelyEnabled)
        whenever(autofillFeatureToggle.isFeatureEnabled(eq(testCase.subfeature.value), any())).thenReturn(subfeatureRemotelyEnabled)
    }

    private suspend fun configureAutofillEnabledByUser(userEnabled: Boolean) {
        whenever(autofillGlobalCapabilityChecker.isAutofillEnabledByUser()).thenReturn(userEnabled)
    }

    private fun configureInternalTester(internalTester: Boolean) {
        whenever(internalTestUserChecker.isInternalTestUser).thenReturn(internalTester)
    }

    private suspend fun configureSecureStorageAvailable(secureStorageAvailable: Boolean) {
        whenever(autofillGlobalCapabilityChecker.isSecureAutofillAvailable()).thenReturn(secureStorageAvailable)
    }

    private suspend fun configureGlobalAutofillCapability(
        isInternalTester: Boolean,
        isGloballyEnabled: Boolean,
    ) {
        val globallyEnabled = if (isInternalTester) {
            true
        } else {
            isGloballyEnabled
        }
        whenever(autofillGlobalCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(globallyEnabled)
    }

    data class TestCase(
        val subfeature: AutofillSubfeatureName = SaveCredentials,
        val expectFeatureEnabled: Boolean,
        val scenario: Scenario,
    )

    data class Scenario(
        val isSecureStorageAvailable: Boolean,
        val isAutofillEnabledByConfig: Boolean,
        val isAutofillEnabledByUser: Boolean,
        val isInternalTester: Boolean,
        val isSubfeatureRemotelyEnabled: Boolean,
    )
}
