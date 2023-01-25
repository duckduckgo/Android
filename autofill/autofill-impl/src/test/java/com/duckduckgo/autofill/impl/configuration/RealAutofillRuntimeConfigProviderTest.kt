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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealAutofillRuntimeConfigProviderTest {
    @Mock
    private lateinit var emailManager: EmailManager

    @Mock
    private lateinit var deviceAuthenticator: DeviceAuthenticator

    @Mock
    private lateinit var autofillStore: AutofillStore

    @Mock
    private lateinit var runtimeConfigurationWriter: RuntimeConfigurationWriter
    private lateinit var testee: RealAutofillRuntimeConfigProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealAutofillRuntimeConfigProvider(
            emailManager,
            deviceAuthenticator,
            autofillStore,
            runtimeConfigurationWriter,
        )

        whenever(runtimeConfigurationWriter.generateContentScope()).thenReturn("")
        whenever(runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(any(), any())).thenReturn("")
        whenever(runtimeConfigurationWriter.generateUserPreferences(any(), any())).thenReturn("")
        whenever(runtimeConfigurationWriter.generateUserUnprotectedDomains()).thenReturn("")
    }

    @Test
    fun whenAutofillEnabledButNoDeviceAuthThenConfigurationUserPrefsCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)

        testee.getRuntimeConfiguration("", url)

        verifyAutofillCredentialsReturnedAs(false)
    }

    @Test
    fun whenAutofillUnavailableThenConfigurationUserPrefsCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(false)

        testee.getRuntimeConfiguration("", url)

        verifyAutofillCredentialsReturnedAs(false)
    }

    @Test
    fun whenAutofillNotEnabledThenConfigurationUserPrefsCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(false)
        whenever(autofillStore.autofillAvailable).thenReturn(true)

        testee.getRuntimeConfiguration("", url)

        verifyAutofillCredentialsReturnedAs(false)
    }

    @Test
    fun whenAutofillEnabledWithDeviceAuthThenConfigurationUserPrefsCredentialsIsTrue() = runTest {
        val url = "example.com"
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
        testee.getRuntimeConfiguration("", url)

        verifyAutofillCredentialsReturnedAs(true)
    }

    @Test
    fun whenCanAutofillThenConfigSpecifiesShowingKeyIcon() = runTest {
        val url = "example.com"
        configureAutofillAvailableForSite(url)
        testee.getRuntimeConfiguration("", url)
        verifyKeyIconRequestedToShow()
    }

    @Test
    fun whenNoCredentialsForUrlThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithCredentialsForUrlThenConfigurationInputTypeCredentialsIsTrue() = runTest {
        val url = "example.com"
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = true, password = true)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithUsernameOnlyForUrlThenConfigurationInputTypeCredentialsUsernameIsTrue() = runTest {
        val url = "example.com"
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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

        testee.getRuntimeConfiguration("", url)

        val expectedCredentialResponse = AvailableInputTypeCredentials(username = false, password = false)
        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = eq(expectedCredentialResponse),
            emailAvailable = any(),
        )
    }

    @Test
    fun whenWithCredentialsForUrlButNoDeviceAuthThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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
    fun whenWithCredentialsForUrlButAutofillDisabledThenConfigurationInputTypeCredentialsIsFalse() = runTest {
        val url = "example.com"
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(false)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
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
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(false)
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
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
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
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(emailManager.isSignedIn()).thenReturn(false)

        testee.getRuntimeConfiguration("", url)

        verify(runtimeConfigurationWriter).generateResponseGetAvailableInputTypes(
            credentialsAvailable = any(),
            emailAvailable = eq(false),
        )
    }

    private suspend fun configureAutofillAvailableForSite(url: String) {
        whenever(autofillStore.getCredentials(url)).thenReturn(emptyList())
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        whenever(autofillStore.autofillEnabled).thenReturn(true)
        whenever(autofillStore.autofillAvailable).thenReturn(true)
    }

    private fun verifyAutofillCredentialsReturnedAs(expectedValue: Boolean) {
        verify(runtimeConfigurationWriter).generateUserPreferences(autofillCredentials = eq(expectedValue), showInlineKeyIcon = any())
    }

    private fun verifyKeyIconRequestedToShow() {
        verify(runtimeConfigurationWriter).generateUserPreferences(showInlineKeyIcon = any(), autofillCredentials = eq(true))
    }
}
