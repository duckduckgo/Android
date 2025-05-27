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
import com.duckduckgo.autofill.FakeSecureStore
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Password
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Username
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.ExactMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UrlOnlyMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMatchDifferentPassword
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMatchMissingPassword
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMissing
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.username.RealAutofillUsernameComparer
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.LastUpdatedTimeProvider
import com.duckduckgo.autofill.sync.CredentialsFixtures.toLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsSyncMetadata
import com.duckduckgo.autofill.sync.SyncCredentialsListener
import com.duckduckgo.autofill.sync.inMemoryAutofillDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SecureStoreBackedAutofillStoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val lastUpdatedTimeProvider = object : LastUpdatedTimeProvider {
        override fun getInMillis(): Long = UPDATED_INITIAL_LAST_UPDATED
    }

    val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    @Mock
    private lateinit var autofillPrefsStore: AutofillPrefsStore
    private lateinit var testee: SecureStoreBackedAutofillStore
    private lateinit var secureStore: FakeSecureStore

    private val autofillUrlMatcher: AutofillUrlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())
    private val usernameComparer = RealAutofillUsernameComparer(autofillFeature, coroutineTestRule.testDispatcherProvider)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(autofillPrefsStore.isEnabled).thenReturn(true)
    }

    @Test
    fun whenAutofillUnavailableAndPrefsAreAllTrueThenReturnTrueForAutofillEnabled() {
        setupTestee(canAccessSecureStorage = false)

        assertTrue(testee.autofillEnabled)
    }

    @Test
    fun whenAutofillAvailableAndPrefsAreAllFalseThenReturnFalseForAutofillEnabled() = runTest {
        setupTesteeWithAutofillAvailable()
        whenever(autofillPrefsStore.isEnabled).thenReturn(false)

        assertFalse(testee.autofillEnabled)
    }

    @Test
    fun whenSecureStorageAvailableThenReturnAutofillAvailable() = runTest {
        setupTesteeWithAutofillAvailable()
        assertTrue(testee.autofillAvailable())
    }

    @Test
    fun whenSecureStorageNotAvailableThenReturnAutofillAvailableFalse() = runTest {
        setupTestee(canAccessSecureStorage = false)
        assertFalse(testee.autofillAvailable())
    }

    @Test
    fun whenAutofillEnabledButCannotAccessSecureStorageThenGetCredentialsWithDomainReturnsEmpty() = runTest {
        setupTestee(canAccessSecureStorage = false)
        val url = "example.com"
        storeCredentials(1, url, "username", "password")

        assertTrue(testee.getCredentials(url).isEmpty())
    }

    @Test
    fun whenStoreEmptyThenNoMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        val result = testee.containsCredentials("example.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsMatchingUsernameEntriesButNoneMatchingUrlThenNoMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("foo.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsDomainButDifferentCredentialsThenUrlMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "differentUsername", "differentPassword")
        assertUrlOnlyMatch(result)
    }

    @Test
    fun whenPasswordExistsForSiteWithNoUsernameThenReturnUsernameMissing() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(id = 1, domain = "https://example.com", username = null, password = "password")
        val result = testee.containsCredentials("example.com", "username", "password")
        assertUsernameMissing(result)
    }

    @Test
    fun whenDifferentPasswordExistsForSiteWithNoUsernameThenReturnUrlMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(id = 1, domain = "https://example.com", username = null, password = "password")
        val result = testee.containsCredentials("example.com", "username", "not-the-same-password")
        assertUrlOnlyMatch(result)
    }

    @Test
    fun whenStoreContainsDomainAndUsernameButDifferentPassword() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "differentPassword")
        assertUsernameMatchDifferentPassword(result)
    }

    @Test
    fun whenStoreContainsDomainAndUsernameButMissingPassword() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", null)
        val result = testee.containsCredentials("example.com", "username", "differentPassword")
        assertUsernameMatchMissingPassword(result)
    }

    @Test
    fun whenStoreContainsMultipleDomainAndUsernameMatchesDifferentPasswordTakesPrecedence() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", null)
        storeCredentials(2, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "differentPassword")
        assertUsernameMatchDifferentPassword(result)
    }

    @Test
    fun whenStoreContainsMatchingDomainAndUsernameAndPasswordThenReturnExactMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "password")
        assertExactMatch(result)
    }

    @Test
    fun whenStoreHasASubdomainThenTopLevelDomainConsideredExactMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://test.example.com", "username", "password")
        val siteToTest = "example.com"

        enableDeepDomainCheckFeatureFlag().also {
            assertExactMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }

        disableDeepDomainCheckFeatureFlag().also {
            assertExactMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }
    }

    @Test
    fun whenStoreHasATopLevelDomainThenSubdomainConsideredExactMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val siteToTest = "test.example.com"

        enableDeepDomainCheckFeatureFlag().also {
            assertExactMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }

        disableDeepDomainCheckFeatureFlag().also {
            assertNotMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }
    }

    @Test
    fun whenStoreHasADifferentSubdomainToVisitedSiteSubdomainThenConsideredExactMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://foo.example.com", "username", "password")
        val siteToTest = "bar.example.com"

        enableDeepDomainCheckFeatureFlag().also {
            assertExactMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }

        disableDeepDomainCheckFeatureFlag().also {
            assertNotMatch(testee.containsCredentials(siteToTest, "username", "password"))
        }
    }

    @Test
    fun whenNoCredentialsForUrlStoredThenGetCredentialsReturnNothing() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "url.com", "username1", "password123")

        assertEquals(0, testee.getCredentials("https://example.com").size)
    }

    @Test
    fun whenNoCredentialsSavedThenGetAllCredentialsReturnNothing() = runTest {
        setupTesteeWithAutofillAvailable()
        assertEquals(0, testee.getAllCredentials().first().size)
    }

    @Test
    fun whenStoredCredentialMissingUsernameAndStoringACredentialWithNoUsernameThenUrlOnlyMatch() = runTest {
        setupTesteeWithAutofillAvailable()
        storeCredentials(1, "https://example.com", username = null, password = "password")
        val result = testee.containsCredentials("example.com", username = null, password = "differentPassword")
        assertUrlOnlyMatch(result)
    }

    @Test
    fun whenPasswordIsUpdatedForUrlThenUpdatedOnlyMatchingCredential() = runTest {
        setupTesteeWithAutofillAvailable()
        val rawUrl = "https://example.com"
        val expectedStoredDomain = "example.com"
        storeCredentials(1, rawUrl, "username1", "password123")
        storeCredentials(2, rawUrl, "username2", "password456")
        storeCredentials(3, rawUrl, "username3", "password789")
        val credentials = LoginCredentials(
            domain = rawUrl,
            username = "username1",
            password = "newpassword",
            id = 1,
        )

        testee.updateCredentials(rawUrl, credentials, Password)

        testee.getCredentials(rawUrl).run {
            this.assertHasLoginCredentials(expectedStoredDomain, "username1", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
            this.assertHasLoginCredentials(expectedStoredDomain, "username2", "password456")
            this.assertHasLoginCredentials(expectedStoredDomain, "username3", "password789")
        }
    }

    @Test
    fun whenStoreHasTopLevelDomainAndUpdateCalledForMatchingTopLevelDomainThenPasswordUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val storedDomain = "example.com"
        val domainFromWebView = "example.com"
        storeCredentials(1, storedDomain, "username1", "password123")
        val credentials = LoginCredentials(domain = domainFromWebView, username = "username1", password = NEW_PASSWORD, id = 1)

        // check behaviour when feature flag disabled
        disableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials, Password)
            assertEquals(NEW_PASSWORD, updated!!.password)
        }

        // check behaviour when feature flag enabled
        enableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials.copy(password = ANOTHER_NEW_PASSWORD), Password)
            assertEquals(ANOTHER_NEW_PASSWORD, updated!!.password)
        }
    }

    @Test
    fun whenStoreHasSubdomainAndUpdateCalledForMatchingTopLevelDomainThenPasswordUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val storedDomain = "test.example.com"
        val domainFromWebView = "example.com"
        storeCredentials(1, storedDomain, "username1", "password123")
        val credentials = LoginCredentials(domain = domainFromWebView, username = "username1", password = NEW_PASSWORD, id = 1)

        // check behaviour when feature flag disabled
        disableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials, Password)
            assertEquals(NEW_PASSWORD, updated!!.password)
        }

        // check behaviour when feature flag enabled
        enableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials.copy(password = ANOTHER_NEW_PASSWORD), Password)
            assertEquals(ANOTHER_NEW_PASSWORD, updated!!.password)
        }
    }

    @Test
    fun whenStoreHasTopLevelDomainAndUpdateCalledForMatchingSubdomainThenPasswordUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val storedDomain = "example.com"
        val domainFromWebView = "test.example.com"
        storeCredentials(1, storedDomain, "username1", "password123")
        val credentials = LoginCredentials(domain = domainFromWebView, username = "username1", password = NEW_PASSWORD, id = 1)

        // check behaviour when feature flag disabled
        disableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials, Password)
            assertNull(updated)
        }

        // check behaviour when feature flag enabled
        enableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials.copy(password = ANOTHER_NEW_PASSWORD), Password)
            assertEquals(ANOTHER_NEW_PASSWORD, updated!!.password)
        }
    }

    @Test
    fun whenStoreHasSubdomainAndUpdateCalledForSiblingSubdomainThenPasswordUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val storedDomain = "foo.example.com"
        val domainFromWebView = "bar.example.com"
        storeCredentials(1, storedDomain, "username1", "password123")
        val credentials = LoginCredentials(domain = domainFromWebView, username = "username1", password = NEW_PASSWORD, id = 1)

        // check behaviour when feature flag disabled
        disableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials, Password)
            assertNull(updated)
        }

        // check behaviour when feature flag enabled
        enableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials.copy(password = ANOTHER_NEW_PASSWORD), Password)
            assertEquals(ANOTHER_NEW_PASSWORD, updated!!.password)
        }
    }

    @Test
    fun whenUpdateCalledForUnrelatedDomainThenPasswordNotUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val storedDomain = "example.com"
        val domainFromWebView = "different-domain.com"
        storeCredentials(1, storedDomain, "username1", "password123")
        val credentials = LoginCredentials(domain = domainFromWebView, username = "username1", password = NEW_PASSWORD, id = 1)

        // check behaviour when feature flag disabled
        disableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials, Password)
            assertNull(updated)
        }

        // check behaviour when feature flag enabled
        enableDeepDomainCheckFeatureFlag().also {
            val updated = testee.updateCredentials(domainFromWebView, credentials.copy(password = ANOTHER_NEW_PASSWORD), Password)
            assertNull(updated)
        }
    }

    @Test
    fun whenPasswordIsUpdatedThenUpdatedOnlyMatchingCredential() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")
        storeCredentials(3, url, "username3", "password789")
        val credentials = LoginCredentials(
            domain = url,
            username = "username1",
            password = "newpassword",
            id = 1,
        )

        testee.updateCredentials(credentials)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "username1", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
            this.assertHasLoginCredentials(url, "username2", "password456")
            this.assertHasLoginCredentials(url, "username3", "password789")
        }
    }

    @Test
    fun whenUpdatingPasswordWithUsernameInDifferentCasingThenOriginalUsernameUntouched() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "username", "password123")

        val incomingCredentials = LoginCredentials(
            domain = url,
            username = "USERNAME",
            password = "newpassword",
            id = 1,
        )

        testee.updateCredentials(url, incomingCredentials, updateType = Password)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "username", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
        }
    }

    @Test
    fun whenUpdateUsernameCalledWithMatchingPasswordAndOriginalAlreadyHasPopulatedUsernameThenIsNotUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "originalUsername", "originalPassword")

        val incomingCredentials = LoginCredentials(
            domain = url,
            username = "username",
            password = "newpassword",
            id = 1,
        )

        testee.updateCredentials(url, incomingCredentials, updateType = Username)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "originalUsername", "originalPassword")
        }
    }

    @Test
    fun whenUpdateUsernameCalledWithMatchingPasswordAndOriginalHasEmptyUsernameThenIsUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "", "originalPassword")

        val incomingCredentials = LoginCredentials(
            domain = url,
            username = "newUsername",
            password = "originalPassword",
            id = 1,
        )

        val updated = testee.updateCredentials(url, incomingCredentials, updateType = Username)
        assertNotNull(updated)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "newUsername", "originalPassword", UPDATED_INITIAL_LAST_UPDATED)
        }
    }

    @Test
    fun whenDomainIsUpdatedTheCleanRawUrl() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "https://example.com"
        storeCredentials(1, url, "username1", "password123")
        val credentials = LoginCredentials(
            domain = "https://www.example.com/test/path",
            username = "username1",
            password = "newpassword",
            id = 1,
        )

        testee.updateCredentials(credentials)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials("www.example.com", "username1", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
        }
    }

    @Test
    fun whenUsernameIsUpdatedForUrlThenUpdatedOnlyMatchingCredential() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, null, "password123")
        storeCredentials(2, url, "username2", "password456")
        storeCredentials(3, url, "username3", "password789")
        val credentials = LoginCredentials(
            domain = url,
            username = "username1",
            password = "password123",
            id = 1,
        )

        testee.updateCredentials(url, credentials, CredentialUpdateType.Username)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "username1", "password123", UPDATED_INITIAL_LAST_UPDATED)
            this.assertHasLoginCredentials(url, "username2", "password456")
            this.assertHasLoginCredentials(url, "username3", "password789")
        }
    }

    @Test
    fun whenUsernameMissingForAnotherUrlThenNoUpdatesMade() = runTest {
        setupTesteeWithAutofillAvailable()
        val urlStored = "example.com"
        val newDomain = "test.com"
        storeCredentials(1, urlStored, null, "password123")

        val credentials = LoginCredentials(domain = newDomain, username = "username1", password = "password123", id = 1)

        assertNull(testee.updateCredentials(newDomain, credentials, CredentialUpdateType.Username))

        testee.getCredentials(urlStored).run {
            this.assertHasLoginCredentials(urlStored, null, "password123")
        }
    }

    @Test
    fun whenSaveCredentialsThenReturnSavedOnGetCredentials() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        val credentials = LoginCredentials(
            id = 1L,
            domain = url,
            username = "username1",
            password = "password",
        )
        testee.saveCredentials(url, credentials)

        assertEquals(credentials.copy(domain = "example.com", lastUpdatedMillis = UPDATED_INITIAL_LAST_UPDATED), testee.getCredentials(url)[0])
    }

    @Test
    fun whenPasswordIsDeletedThenRemoveCredentialFromStore() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")
        storeCredentials(3, url, "username3", "password789")
        testee.deleteCredentials(1)

        testee.getCredentials(url).run {
            this.assertHasNoLoginCredentials(url, "username1", "password123")
            this.assertHasLoginCredentials(url, "username2", "password456")
            this.assertHasLoginCredentials(url, "username3", "password789")
        }
    }

    @Test
    fun whenCredentialWithIdIsStoredTheReturnCredentialsOnGetCredentialsWithId() = runTest {
        setupTesteeWithAutofillAvailable()
        val url = "example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")

        assertEquals(
            LoginCredentials(
                id = 1,
                domain = url,
                username = "username1",
                password = "password123",
                lastUpdatedMillis = DEFAULT_INITIAL_LAST_UPDATED,
                notes = "notes",
            ),
            testee.getCredentialsWithId(1),
        )
    }

    @Test
    fun whenNoCredentialStoredTheReturnNullOnGetCredentialsWithId() = runTest {
        setupTesteeWithAutofillAvailable()
        assertNull(testee.getCredentialsWithId(1))
    }

    @Test
    fun whenExactUrlMatchesThenCredentialsReturned() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://example.com"
        val visitedSite = "https://example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
        assertEquals(1L, results[0].id)
    }

    @Test
    fun whenExactSubdomainMatchesThenCredentialsReturned() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://subdomain.example.com"
        val visitedSite = "https://subdomain.example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
        assertEquals(1L, results[0].id)
    }

    @Test
    fun whenExactWwwSubdomainMatchesThenCredentialsReturned() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://www.example.com"
        val visitedSite = "https://www.example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
        assertEquals(1L, results[0].id)
    }

    @Test
    fun whenSavedCredentialHasSubdomainAndVisitedPageDoesNotThenCredentialMatched() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://test.example.com"
        val visitedSite = "https://example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
    }

    @Test
    fun whenSavedCredentialHasNoSubdomainAndVisitedPageDoesThenCredentialNotMatched() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://example.com"
        val visitedSite = "https://test.example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
    }

    @Test
    fun whenSavedCredentialHasDifferentSubdomainToVisitedPageSubdomainThenCredentialMatched() = runTest {
        setupTesteeWithAutofillAvailable()
        val savedUrl = "https://test.example.com"
        val visitedSite = "https://different.example.com"
        storeCredentials(1, savedUrl, "username1", "password123")

        val results = testee.getCredentials(visitedSite)
        assertEquals(1, results.size)
    }

    @Test
    fun whenReinsertingLoginCredentialsThenAllFieldsPreserved() = runTest {
        setupTesteeWithAutofillAvailable()
        val originalCredentials = LoginCredentials(
            id = 123,
            domain = "example.com",
            username = "user",
            password = "abc123",
            domainTitle = "title",
            notes = "notes",
            lastUpdatedMillis = 1000,
        )
        testee.reinsertCredentials(originalCredentials)

        val reinsertedCredentials = secureStore.getWebsiteLoginDetailsWithCredentials(123)
        assertNotNull("Failed to find credentials", reinsertedCredentials)
        assertEquals(originalCredentials.id, reinsertedCredentials!!.details.id)
        assertEquals(originalCredentials.username, reinsertedCredentials.details.username)
        assertEquals(originalCredentials.domain, reinsertedCredentials.details.domain)
        assertEquals(originalCredentials.domainTitle, reinsertedCredentials.details.domainTitle)
        assertEquals(originalCredentials.lastUpdatedMillis, reinsertedCredentials.details.lastUpdatedMillis)
        assertEquals(originalCredentials.notes, reinsertedCredentials.notes)
        assertEquals(originalCredentials.password, reinsertedCredentials.password)
    }

    @Test
    fun whenUpdatingCredentialsByDefaultLastUpdatedTimestampGetsUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val saved = storeCredentials(id = 1, domain = "example.com", username = "username", password = "password")
        val updated = testee.updateCredentials(saved)!!
        assertEquals(UPDATED_INITIAL_LAST_UPDATED, updated.lastUpdatedMillis)
    }

    @Test
    fun whenUpdatingCredentialsAndSpecifyNotToUpdateLastUpdatedTimestampThenNotUpdated() = runTest {
        setupTesteeWithAutofillAvailable()
        val saved = storeCredentials(id = 1, domain = "example.com", username = "username", password = "password")
        val updated = testee.updateCredentials(saved, refreshLastUpdatedTimestamp = false)!!
        assertEquals(DEFAULT_INITIAL_LAST_UPDATED, updated.lastUpdatedMillis)
    }

    @Test
    fun whenUserHasNeverImportedPasswordThenCallsThroughToStoreCorrectly() = runTest {
        setupTesteeWithAutofillAvailable()
        testee.hasEverImportedPasswords
        verify(autofillPrefsStore).hasEverImportedPasswords
    }

    @Test
    fun whenUserHasImportedPasswordThenCallsThroughToStoreCorrectly() = runTest {
        setupTesteeWithAutofillAvailable()
        testee.hasEverImportedPasswords = true
        verify(autofillPrefsStore).hasEverImportedPasswords = true
    }

    @Test
    fun whenUserHasNeverDismissedImportPasswordPromoThenCallsThroughToStoreCorrectly() = runTest {
        setupTesteeWithAutofillAvailable()
        testee.hasDismissedImportedPasswordsPromo
        verify(autofillPrefsStore).hasDismissedImportedPasswordsPromo
    }

    @Test
    fun whenUserHasDismissedImportPasswordPromoThenCallsThroughToStoreCorrectly() = runTest {
        setupTesteeWithAutofillAvailable()
        testee.hasDismissedImportedPasswordsPromo = true
        verify(autofillPrefsStore).hasDismissedImportedPasswordsPromo = true
    }

    private fun enableDeepDomainCheckFeatureFlag() {
        autofillFeature.deepDomainComparisonsOnExistingCredentialsChecks().setRawStoredState(State(true))
    }

    private fun disableDeepDomainCheckFeatureFlag() {
        autofillFeature.deepDomainComparisonsOnExistingCredentialsChecks().setRawStoredState(State(false))
    }

    private fun List<LoginCredentials>.assertHasNoLoginCredentials(
        url: String,
        username: String,
        password: String,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED,
    ) {
        val result = this.filter {
            it.domain == url && it.username == username && it.password == password && it.lastUpdatedMillis == lastUpdatedTimeMillis
        }
        assertEquals(0, result.size)
    }

    private fun List<LoginCredentials>.assertHasLoginCredentials(
        url: String,
        username: String?,
        password: String,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED,
    ) {
        val result = this.filter {
            it.domain == url && it.username == username && it.password == password && it.lastUpdatedMillis == lastUpdatedTimeMillis
        }
        assertEquals(1, result.size)
    }

    private fun setupTesteeWithAutofillAvailable() {
        setupTestee(canAccessSecureStorage = true)
    }

    private fun setupTestee(
        canAccessSecureStorage: Boolean,
    ) {
        secureStore = FakeSecureStore(canAccessSecureStorage, autofillUrlMatcher)
        testee = SecureStoreBackedAutofillStore(
            secureStorage = secureStore,
            lastUpdatedTimeProvider = lastUpdatedTimeProvider,
            autofillPrefsStore = autofillPrefsStore,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            autofillUrlMatcher = autofillUrlMatcher,
            passwordStoreEventListenersPlugins = FakePasswordStoreEventPlugin(),
            syncCredentialsListener = SyncCredentialsListener(
                CredentialsSyncMetadata(inMemoryAutofillDatabase().credentialsSyncDao()),
                coroutineTestRule.testDispatcherProvider,
                coroutineTestRule.testScope,
            ),
            autofillFeature = autofillFeature,
            usernameComparer = usernameComparer,
        )
    }

    private fun assertNotMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected NoMatch but was %s", result.javaClass.simpleName), result is NoMatch)
    }

    private fun assertUrlOnlyMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected UrlOnlyMatch but was %s", result.javaClass.simpleName), result is UrlOnlyMatch)
    }

    private fun assertUsernameMissing(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected UsernameMissing but was %s", result.javaClass.simpleName), result is UsernameMissing)
    }

    private fun assertUsernameMatchDifferentPassword(result: ContainsCredentialsResult) {
        assertTrue(
            String.format("Expected UsernameMatchDifferentPassword but was %s", result.javaClass.simpleName),
            result is UsernameMatchDifferentPassword,
        )
    }

    private fun assertUsernameMatchMissingPassword(result: ContainsCredentialsResult) {
        assertTrue(
            String.format("Expected UsernameMatchMissingPassword but was %s", result.javaClass.simpleName),
            result is UsernameMatchMissingPassword,
        )
    }

    private fun assertExactMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected ExactMatch but was %s", result.javaClass.simpleName), result is ExactMatch)
    }

    private suspend fun storeCredentials(
        id: Long,
        domain: String,
        username: String?,
        password: String?,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED,
        notes: String = "notes",
    ): LoginCredentials {
        val cleanedDomain = autofillUrlMatcher.cleanRawUrl(domain)
        val details = WebsiteLoginDetails(domain = cleanedDomain, username = username, id = id, lastUpdatedMillis = lastUpdatedTimeMillis)
        val credentials = WebsiteLoginDetailsWithCredentials(details, password, notes)
        return secureStore.addWebsiteLoginDetailsWithCredentials(credentials).toLoginCredentials()
    }

    companion object {
        private const val DEFAULT_INITIAL_LAST_UPDATED = 200L
        private const val UPDATED_INITIAL_LAST_UPDATED = 10000L

        private const val NEW_PASSWORD = "new-password"
        private const val ANOTHER_NEW_PASSWORD = "another-new-password"
    }
}
