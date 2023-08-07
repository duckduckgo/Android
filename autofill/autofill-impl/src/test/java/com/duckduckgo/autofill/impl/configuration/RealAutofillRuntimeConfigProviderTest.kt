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

import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealAutofillRuntimeConfigProviderTest {

    private lateinit var testee: RealAutofillRuntimeConfigProvider

    private val emailManager: EmailManager = mock()
    private val autofillStore: AutofillStore = mock()
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter = mock()
    private val shareableCredentials: ShareableCredentials = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealAutofillRuntimeConfigProvider(
            emailManager,
            autofillStore,
            runtimeConfigurationWriter,
            autofillCapabilityChecker = autofillCapabilityChecker,
            shareableCredentials = shareableCredentials,
        )

        runTest {
            whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
        }

        whenever(runtimeConfigurationWriter.generateContentScope()).thenReturn("")
        whenever(runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(any(), any())).thenReturn("")
        whenever(runtimeConfigurationWriter.generateUserUnprotectedDomains()).thenReturn("")
        whenever(
            runtimeConfigurationWriter.generateUserPreferences(
                autofillCredentials = any(),
                credentialSaving = any(),
                passwordGeneration = any(),
                showInlineKeyIcon = any(),
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
    fun whenNoCredentialsForUrlThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
        configureNoShareableLogins()
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
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        configureNoShareableLogins()
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
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        configureNoShareableLogins()
        whenever(emailManager.isSignedIn()).thenReturn(false)

        testee.getRuntimeConfiguration("", url)

        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = any(),
            emailAvailable = eq(false),
        )
    }

    private suspend fun configureAutofillAvailableForSite(url: String) {
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
    }

    private suspend fun configureAutofillCapabilities(enabled: Boolean) {
        whenever(autofillCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canInjectCredentialsToWebView(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canSaveCredentialsFromWebView(any())).thenReturn(enabled)
        whenever(autofillCapabilityChecker.canGeneratePasswordFromWebView(any())).thenReturn(enabled)
    }

    private fun verifyAutofillCredentialsReturnedAs(expectedValue: Boolean) {
        verify(runtimeConfigurationWriter).generateUserPreferences(
            autofillCredentials = eq(expectedValue),
            credentialSaving = any(),
            passwordGeneration = any(),
            showInlineKeyIcon = any(),
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
        )
    }

    companion object {
        private const val EXAMPLE_URL = "example.com"
    }
}
