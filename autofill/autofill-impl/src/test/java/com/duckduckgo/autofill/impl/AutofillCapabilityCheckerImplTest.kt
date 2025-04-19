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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillCapabilityCheckerImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker = mock()

    private lateinit var testee: AutofillCapabilityCheckerImpl

    @Test
    fun whenTopLevelFeatureDisabledAndDisabledByUserThenCannotAccessAnySubFeatures() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = false)
        assertAllSubFeaturesDisabled()
    }

    @Test
    fun whenTopLevelFeatureDisabledButEnabledByUserThenCannotAccessAnySubFeatures() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = true)
        assertAllSubFeaturesDisabled()
    }

    /*
        User has autofill enabled and top level feature enabled --- tests for the subfeatures
     */

    @Test
    fun whenUserHasAutofillEnabledThenCanInjectCredentialsDictatedByConfig() = runTest {
        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canInjectCredentials = true)
        assertTrue(testee.canInjectCredentialsToWebView(URL))

        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canInjectCredentials = false)
        assertFalse(testee.canInjectCredentialsToWebView(URL))
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanSaveCredentialsDictatedByConfig() = runTest {
        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canSaveCredentials = true)
        assertTrue(testee.canSaveCredentialsFromWebView(URL))

        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canSaveCredentials = false)
        assertFalse(testee.canSaveCredentialsFromWebView(URL))
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanAccessCredentialManagementScreenDictatedByConfig() = runTest {
        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canAccessCredentialManagement = true)
        assertTrue(testee.canAccessCredentialManagementScreen())

        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canAccessCredentialManagement = false)
        assertFalse(testee.canAccessCredentialManagementScreen())
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanGeneratePasswordFromWebViewDictatedByConfig() = runTest {
        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canGeneratePassword = true)
        assertTrue(testee.canGeneratePasswordFromWebView(URL))

        setupConfig(topLevelFeatureEnabled = true, autofillEnabledByUser = true, canGeneratePassword = false)
        assertFalse(testee.canGeneratePasswordFromWebView(URL))
    }

    /*
        User has autofill enabled and top level feature disabled --- tests for the subfeatures
     */

    @Test
    fun whenUserHasAutofillEnabledButTopLevelFeatureDisabledThenCanInjectCredentialsIsFalse() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = true)
        assertFalse(testee.canInjectCredentialsToWebView(URL))
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanSaveCredentialsIsFalse() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = true)
        assertFalse(testee.canSaveCredentialsFromWebView(URL))
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanAccessCredentialManagementScreenIsFalse() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = true)
        assertFalse(testee.canAccessCredentialManagementScreen())
    }

    @Test
    fun whenUserHasAutofillEnabledThenCanGeneratePasswordFromWebViewIsFalse() = runTest {
        setupConfig(topLevelFeatureEnabled = false, autofillEnabledByUser = true)
        assertFalse(testee.canGeneratePasswordFromWebView(URL))
    }

    private suspend fun assertAllSubFeaturesDisabled() {
        assertFalse(testee.canAccessCredentialManagementScreen())
        assertFalse(testee.canGeneratePasswordFromWebView(URL))
        assertFalse(testee.canInjectCredentialsToWebView(URL))
        assertFalse(testee.canSaveCredentialsFromWebView(URL))
    }

    @SuppressLint("DenyListedApi")
    private suspend fun setupConfig(
        topLevelFeatureEnabled: Boolean = false,
        autofillEnabledByUser: Boolean = false,
        canInjectCredentials: Boolean = false,
        canSaveCredentials: Boolean = false,
        canGeneratePassword: Boolean = false,
        canAccessCredentialManagement: Boolean = false,
    ) {
        val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
        autofillFeature.self().setRawStoredState(State(enable = topLevelFeatureEnabled))
        autofillFeature.canInjectCredentials().setRawStoredState(State(enable = canInjectCredentials))
        autofillFeature.canSaveCredentials().setRawStoredState(State(enable = canSaveCredentials))
        autofillFeature.canGeneratePasswords().setRawStoredState(State(enable = canGeneratePassword))
        autofillFeature.canAccessCredentialManagement().setRawStoredState(State(enable = canAccessCredentialManagement))

        whenever(autofillGlobalCapabilityChecker.isSecureAutofillAvailable()).thenReturn(true)
        whenever(autofillGlobalCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(true)
        whenever(autofillGlobalCapabilityChecker.isAutofillEnabledByUser()).thenReturn(autofillEnabledByUser)
        whenever(internalTestUserChecker.isInternalTestUser).thenReturn(false)

        testee = AutofillCapabilityCheckerImpl(
            autofillFeature = autofillFeature,
            internalTestUserChecker = internalTestUserChecker,
            autofillGlobalCapabilityChecker = autofillGlobalCapabilityChecker,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    companion object {
        private const val URL = "https://example.com"
    }
}
