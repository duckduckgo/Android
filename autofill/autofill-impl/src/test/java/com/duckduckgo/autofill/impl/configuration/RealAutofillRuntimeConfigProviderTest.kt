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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.incontext.availability.EmailProtectionInContextAvailabilityRules
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
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

    private val emailManager: EmailManager = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter = mock()
    private val shareableCredentials: ShareableCredentials = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()
    private val siteSpecificFixesStore: AutofillSiteSpecificFixesStore = mock()
    private val emailProtectionInContextAvailabilityRules: EmailProtectionInContextAvailabilityRules = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealAutofillRuntimeConfigProvider(
            emailManager,
            autofillStore,
            runtimeConfigurationWriter,
            autofillCapabilityChecker = autofillCapabilityChecker,
            autofillFeature = autofillFeature,
            shareableCredentials = shareableCredentials,
            emailProtectionInContextAvailabilityRules = emailProtectionInContextAvailabilityRules,
            neverSavedSiteRepository = neverSavedSiteRepository,
            siteSpecificFixesStore = siteSpecificFixesStore,
        )

        runTest {
            whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
            whenever(neverSavedSiteRepository.isInNeverSaveList(any())).thenReturn(false)
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
        whenever(runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(any(), any())).thenReturn("")
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
        testee.getRuntimeConfiguration("", EXAMPLE_URL)
        verifyAutofillCredentialsReturnedAs(false)
    }

    @Test
    fun whenAutofillEnabledThenConfigurationUserPrefsCredentialsIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        configureNoShareableLogins()
        testee.getRuntimeConfiguration("", EXAMPLE_URL)
        verifyAutofillCredentialsReturnedAs(true)
    }

    @Test
    fun whenCanAutofillThenConfigSpecifiesShowingKeyIcon() = runTest {
        configureAutofillCapabilities(enabled = true)
        configureAutofillAvailableForSite(EXAMPLE_URL)
        configureNoShareableLogins()
        testee.getRuntimeConfiguration("", EXAMPLE_URL)
        verifyKeyIconRequestedToShow()
    }

    @Test
    fun whenCanCategorizePasswordVariantEnabledThenConfigurationUserPrefsReflectsThat() = runTest {
        configureAutofillCapabilities(enabled = false)
        autofillFeature.passwordVariantCategorization().setRawStoredState(State(enable = true))
        testee.getRuntimeConfiguration("", EXAMPLE_URL)
        verifyCanCategorizePasswordVariant(true)
    }

    @Test
    fun whenCanCategorizePasswordVariantDisabledThenConfigurationUserPrefsReflectsThat() = runTest {
        configureAutofillCapabilities(enabled = false)
        autofillFeature.passwordVariantCategorization().setRawStoredState(State(enable = false))
        testee.getRuntimeConfiguration("", EXAMPLE_URL)
        verifyCanCategorizePasswordVariant(false)
    }

    @Test
    fun whenNoCredentialsForUrlThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        configureAutofillEnabledWithNoSavedCredentials(EXAMPLE_URL)
        testee.getRuntimeConfiguration("", EXAMPLE_URL)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithCredentialsForUrlThenConfigurationInputTypeCredentialsIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = "password",
                ),
            ),
        )
        configureNoShareableLogins()

        testee.getRuntimeConfiguration("", EXAMPLE_URL)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = true, password = true)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithShareableCredentialsForUrlThenConfigurationInputTypeCredentialsIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
        whenever(shareableCredentials.shareableCredentials(any())).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = "password",
                ),
            ),
        )

        testee.getRuntimeConfiguration("", EXAMPLE_URL)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = true, password = true)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithUsernameOnlyForUrlThenConfigurationInputTypeCredentialsUsernameIsTrue() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = "username",
                    password = null,
                ),
            ),
        )
        configureNoShareableLogins()

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = true, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithEmptyUsernameOnlyForUrlThenConfigurationInputTypeCredentialsUsernameIsFalse() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = "",
                    password = null,
                ),
            ),
        )
        configureNoShareableLogins()

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithPasswordOnlyForUrlThenConfigurationInputTypeCredentialsUsernameIsTrue() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = null,
                    password = "password",
                ),
            ),
        )
        configureNoShareableLogins()

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = true)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithEmptyPasswordOnlyForUrlThenConfigurationInputTypeCredentialsUsernameIsTrue() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = null,
                    password = "",
                ),
            ),
        )
        configureNoShareableLogins()

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithCredentialsForUrlButAutofillDisabledThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = false)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = "username",
                    password = "password",
                ),
            ),
        )

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithCredentialsForUrlButAutofillUnavailableThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        val url = "example.com"
        configureAutofillCapabilities(enabled = false)
        whenever(autofillStore.getCredentials(url)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = url,
                    username = "username",
                    password = "password",
                ),
            ),
        )

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenEmailIsSignedInThenConfigurationInputTypeEmailIsTrue() = runTest {
        val url = "example.com"
        configureAutofillEnabledWithNoSavedCredentials(url)
        whenever(emailManager.isSignedIn()).thenReturn(true)

        testee.getRuntimeConfiguration("", url)

        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = any(),
            emailAvailable = eq(true),
        )
    }

    @Test
    fun whenEmailIsSignedOutThenConfigurationInputTypeEmailIsFalse() = runTest {
        val url = "example.com"
        configureAutofillEnabledWithNoSavedCredentials(url)
        whenever(emailManager.isSignedIn()).thenReturn(false)

        testee.getRuntimeConfiguration("", url)

        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = any(),
            emailAvailable = eq(false),
        )
    }

    @Test
    fun whenSiteNotInNeverSaveListThenCanSaveCredentials() = runTest {
        val url = "example.com"
        configureAutofillEnabledWithNoSavedCredentials(url)
        testee.getRuntimeConfiguration("", url)
        verifyCanSaveCredentialsReturnedAs(true)
    }

    @Test
    fun whenSiteInNeverSaveListThenStillTellJsWeCanSaveCredentials() = runTest {
        val url = "example.com"
        configureAutofillEnabledWithNoSavedCredentials(url)
        whenever(neverSavedSiteRepository.isInNeverSaveList(url)).thenReturn(true)

        testee.getRuntimeConfiguration("", url)
        verifyCanSaveCredentialsReturnedAs(true)
    }

    private suspend fun RealAutofillRuntimeConfigProviderTest.configureAutofillEnabledWithNoSavedCredentials(url: String) {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        configureNoShareableLogins()
    }

    private suspend fun configureAutofillAvailableForSite(url: String) {
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable()).thenReturn(true)
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

    private suspend fun configureNoShareableLogins() {
        whenever(shareableCredentials.shareableCredentials(any())).thenReturn(emptyList())
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
