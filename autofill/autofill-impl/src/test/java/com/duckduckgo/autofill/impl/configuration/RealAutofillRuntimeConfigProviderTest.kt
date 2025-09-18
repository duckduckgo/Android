/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.configuration.AutofillAvailableInputTypesProvider.AvailableInputTypes
import com.duckduckgo.autofill.impl.email.incontext.availability.EmailProtectionInContextAvailabilityRules
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.store.emptyReAuthenticationDetails
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealAutofillRuntimeConfigProviderTest {

    private lateinit var testee: RealAutofillRuntimeConfigProvider

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()
    private val siteSpecificFixesStore: AutofillSiteSpecificFixesStore = mock()
    private val emailProtectionInContextAvailabilityRules: EmailProtectionInContextAvailabilityRules = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val availableInputTypesProvider: AutofillAvailableInputTypesProvider = mock()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealAutofillRuntimeConfigProvider(
            runtimeConfigurationWriter = runtimeConfigurationWriter,
            autofillCapabilityChecker = autofillCapabilityChecker,
            autofillFeature = autofillFeature,
            emailProtectionInContextAvailabilityRules = emailProtectionInContextAvailabilityRules,
            neverSavedSiteRepository = neverSavedSiteRepository,
            siteSpecificFixesStore = siteSpecificFixesStore,
            autofillAvailableInputTypesProvider = availableInputTypesProvider,
        )

        runTest {
            whenever(neverSavedSiteRepository.isInNeverSaveList(any())).thenReturn(false)
            whenever(availableInputTypesProvider.getTypes(any(), any())).thenReturn(
                AvailableInputTypes(
                    username = false,
                    password = false,
                    email = false,
                    credentialsImport = false,
                ),
            )
        }

        autofillFeature.canCategorizeUnknownUsername().setRawStoredState(State(enable = true))
        whenever(
            runtimeConfigurationWriter.generateContentScope(
                AutofillSiteSpecificFixesSettings(
                    javascriptConfigSiteSpecificFixes = "",
                    canApplySiteSpecificFixes = false,
                ),
            ),
        ).thenReturn("")
        whenever(runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(any())).thenReturn("")
        whenever(runtimeConfigurationWriter.generateUserUnprotectedDomains()).thenReturn("")
        whenever(
            runtimeConfigurationWriter.generateUserPreferences(
                autofillCredentials = any(),
                credentialSaving = any(),
                passwordGeneration = any(),
                showInlineKeyIcon = any(),
                showInContextEmailProtectionSignup = any(),
                unknownUsernameCategorization = any(),
                canCategorizePasswordVariant = any(),
                partialFormSaves = any(),
            ),
        ).thenReturn("")
    }

    @Test
    fun whenAutofillNotEnabledThenConfigurationUserPrefsCredentialsIsFalse() = runTest {
        configureAutofillCapabilities(enabled = false)
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyAutofillCredentialsReturnedAs(false)
    }

    @Test
    fun whenAutofillEnabledThenConfigurationUserPrefsCredentialsIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyAutofillCredentialsReturnedAs(true)
    }

    @Test
    fun whenCanAutofillThenConfigSpecifiesShowingKeyIcon() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(availableInputTypesProvider.getTypes(EXAMPLE_URL)).thenReturn(
            AvailableInputTypes(
                username = true,
                password = true,
                email = false,
                credentialsImport = false,
            ),
        )
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyKeyIconRequestedToShow()
    }

    @Test
    fun whenCanCategorizePasswordVariantEnabledThenConfigurationUserPrefsReflectsThat() = runTest {
        configureAutofillCapabilities(enabled = false)
        autofillFeature.passwordVariantCategorization().setRawStoredState(State(enable = true))
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyCanCategorizePasswordVariant(true)
    }

    @Test
    fun whenCanCategorizePasswordVariantDisabledThenConfigurationUserPrefsReflectsThat() = runTest {
        configureAutofillCapabilities(enabled = false)
        autofillFeature.passwordVariantCategorization().setRawStoredState(State(enable = false))
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyCanCategorizePasswordVariant(false)
    }

    @Test
    fun whenAvailableInputTypesProviderCalledThenResultPassedToWriter() = runTest {
        configureAutofillCapabilities(enabled = true)
        val expectedInputTypes = AvailableInputTypes(
            username = true,
            password = true,
            email = true,
            credentialsImport = true,
        )
        whenever(availableInputTypesProvider.getTypes(EXAMPLE_URL)).thenReturn(expectedInputTypes)

        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())

        verify(availableInputTypesProvider).getTypes(EXAMPLE_URL)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(eq(expectedInputTypes))
    }

    @Test
    fun whenSiteNotInNeverSaveListThenCanSaveCredentials() = runTest {
        configureAutofillCapabilities(enabled = true)
        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyCanSaveCredentialsReturnedAs(true)
    }

    @Test
    fun whenSiteInNeverSaveListThenStillTellJsWeCanSaveCredentials() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(neverSavedSiteRepository.isInNeverSaveList(EXAMPLE_URL)).thenReturn(true)

        testee.getRuntimeConfiguration("", EXAMPLE_URL, emptyReAuthenticationDetails())
        verifyCanSaveCredentialsReturnedAs(true)
    }

    private suspend fun configureAutofillCapabilities(enabled: Boolean) {
        whenever(autofillCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canInjectCredentialsToWebView(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canSaveCredentialsFromWebView(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canGeneratePasswordFromWebView(any())).thenReturn(enabled)
        whenever(emailProtectionInContextAvailabilityRules.permittedToShow(any())).thenReturn(enabled)
    }

    private fun verifyAutofillCredentialsReturnedAs(expectedValue: Boolean) {
        verify(runtimeConfigurationWriter).generateUserPreferences(
            autofillCredentials = eq(expectedValue),
            credentialSaving = any(),
            passwordGeneration = any(),
            showInlineKeyIcon = any(),
            showInContextEmailProtectionSignup = any(),
            unknownUsernameCategorization = any(),
            canCategorizePasswordVariant = any(),
            partialFormSaves = any(),
        )
    }

    private fun verifyCanSaveCredentialsReturnedAs(expected: Boolean) {
        verify(runtimeConfigurationWriter).generateUserPreferences(
            autofillCredentials = any(),
            credentialSaving = eq(expected),
            passwordGeneration = any(),
            showInlineKeyIcon = any(),
            showInContextEmailProtectionSignup = any(),
            unknownUsernameCategorization = any(),
            canCategorizePasswordVariant = any(),
            partialFormSaves = any(),
        )
    }

    private fun verifyCanCategorizePasswordVariant(expected: Boolean) {
        verify(runtimeConfigurationWriter).generateUserPreferences(
            autofillCredentials = any(),
            credentialSaving = any(),
            passwordGeneration = any(),
            showInlineKeyIcon = any(),
            showInContextEmailProtectionSignup = any(),
            unknownUsernameCategorization = any(),
            canCategorizePasswordVariant = eq(expected),
            partialFormSaves = any(),
        )
    }

    private fun verifyKeyIconRequestedToShow() {
        verify(runtimeConfigurationWriter).generateUserPreferences(
            autofillCredentials = any(),
            credentialSaving = any(),
            passwordGeneration = any(),
            showInlineKeyIcon = eq(true),
            showInContextEmailProtectionSignup = any(),
            unknownUsernameCategorization = any(),
            canCategorizePasswordVariant = any(),
            partialFormSaves = any(),
        )
    }

    companion object {
        private const val EXAMPLE_URL = "example.com"
    }
}
