/*
 * Copyright (c) 2025 DuckDuckGo
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.importing.InBrowserImportPromo
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAutofillAvailableInputTypesProviderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: RealAutofillAvailableInputTypesProvider

    private val emailManager: EmailManager = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val shareableCredentials: ShareableCredentials = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()
    private val inBrowserPromo: InBrowserImportPromo = mock()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealAutofillAvailableInputTypesProvider(
            emailManager = emailManager,
            autofillStore = autofillStore,
            shareableCredentials = shareableCredentials,
            autofillCapabilityChecker = autofillCapabilityChecker,
            inBrowserPromo = inBrowserPromo,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

        // Default setup
        runTest {
            whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
            whenever(shareableCredentials.shareableCredentials(any())).thenReturn(emptyList())
            whenever(emailManager.isSignedIn()).thenReturn(false)
            whenever(inBrowserPromo.canShowPromo(any(), anyOrNull())).thenReturn(false)
            whenever(autofillCapabilityChecker.canInjectCredentialsToWebView(any())).thenReturn(true)
        }
    }

    @Test
    fun whenNoCredentialsForUrlThenUsernameAndPasswordAreFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
        whenever(shareableCredentials.shareableCredentials(EXAMPLE_URL)).thenReturn(emptyList())

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.username)
        assertFalse(result.password)
    }

    @Test
    fun whenWithCredentialsForUrlThenUsernameAndPasswordAreTrue() = runTest {
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

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenWithShareableCredentialsForUrlThenUsernameAndPasswordAreTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(emptyList())
        whenever(shareableCredentials.shareableCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenWithUsernameOnlyForUrlThenOnlyUsernameIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = null,
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertFalse(result.password)
    }

    @Test
    fun whenWithEmptyUsernameForUrlThenUsernameIsFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "",
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenWithPasswordOnlyForUrlThenOnlyPasswordIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = null,
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenWithEmptyPasswordForUrlThenPasswordIsFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = "",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertFalse(result.password)
    }

    @Test
    fun whenWithCredentialsButAutofillDisabledThenUsernameAndPasswordAreFalse() = runTest {
        configureAutofillCapabilities(enabled = false)
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

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.username)
        assertFalse(result.password)
    }

    @Test
    fun whenUrlIsNullThenUsernameAndPasswordAreFalse() = runTest {
        // Even if we have credentials, null URL should return false
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = "example.com",
                    username = "username",
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(null)

        assertFalse(result.username)
        assertFalse(result.password)
    }

    @Test
    fun whenEmailIsSignedInThenEmailIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(emailManager.isSignedIn()).thenReturn(true)

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.email)
    }

    @Test
    fun whenEmailIsSignedOutThenEmailIsFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(emailManager.isSignedIn()).thenReturn(false)

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.email)
    }

    @Test
    fun whenImportPromoCanShowThenCredentialsImportIsTrue() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(inBrowserPromo.canShowPromo(any(), anyOrNull())).thenReturn(true)

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.credentialsImport)
    }

    @Test
    fun whenImportPromoCannotShowThenCredentialsImportIsFalse() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(inBrowserPromo.canShowPromo(any(), anyOrNull())).thenReturn(false)

        val result = testee.getTypes(EXAMPLE_URL)

        assertFalse(result.credentialsImport)
    }

    @Test
    fun whenMultipleCredentialsWithDifferentFieldsThenCombinesCorrectly() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = null,
                ),
                LoginCredentials(
                    id = 2,
                    domain = EXAMPLE_URL,
                    username = null,
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenCombiningDirectAndShareableCredentialsThenBothAreConsidered() = runTest {
        configureAutofillCapabilities(enabled = true)
        whenever(autofillStore.getCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 1,
                    domain = EXAMPLE_URL,
                    username = "username",
                    password = null,
                ),
            ),
        )
        whenever(shareableCredentials.shareableCredentials(EXAMPLE_URL)).thenReturn(
            listOf(
                LoginCredentials(
                    id = 2,
                    domain = EXAMPLE_URL,
                    username = null,
                    password = "password",
                ),
            ),
        )

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertTrue(result.password)
    }

    @Test
    fun whenAllFeaturesEnabledThenAllTypesAreTrue() = runTest {
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
        whenever(emailManager.isSignedIn()).thenReturn(true)
        whenever(inBrowserPromo.canShowPromo(any(), anyOrNull())).thenReturn(true)

        val result = testee.getTypes(EXAMPLE_URL)

        assertTrue(result.username)
        assertTrue(result.password)
        assertTrue(result.email)
        assertTrue(result.credentialsImport)
    }

    private suspend fun configureAutofillCapabilities(enabled: Boolean) {
        whenever(autofillCapabilityChecker.canInjectCredentialsToWebView(any())).thenReturn(enabled)
    }

    companion object {
        private const val EXAMPLE_URL = "example.com"
    }
}
