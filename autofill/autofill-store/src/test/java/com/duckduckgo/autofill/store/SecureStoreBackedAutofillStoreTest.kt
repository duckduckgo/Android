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

package com.duckduckgo.autofill.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.InternalTestUserChecker
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult.*
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SecureStoreBackedAutofillStoreTest {

    private val context: Context = getApplicationContext()
    private val lastUpdatedTimeProvider = object : LastUpdatedTimeProvider {
        override fun getInMillis(): Long = UPDATED_INITIAL_LAST_UPDATED
    }
    private lateinit var testee: SecureStoreBackedAutofillStore
    private lateinit var internalTestUserChecker: FakeInternalTestUserChecker
    private lateinit var secureStore: FakeSecureStore

    @Test
    fun whenInternalTestUserTrueThenReturnAutofillEnabled() {
        setupTesteeWithAutofillEnabledAndAvailable()
        assertTrue(testee.autofillAvailable)
    }

    @Test
    fun whenInternalTestUserFalseThenReturnAutofillAvailableFalse() {
        setupTestee(isInternalUser = false, canAccessSecureStorage = true)
        assertFalse(testee.autofillAvailable)
    }

    @Test
    fun whenInternalTestUserButCantAccessSecureStorageThenReturnAutofillAvailableFalse() {
        setupTestee(isInternalUser = true, canAccessSecureStorage = false)
        assertFalse(testee.autofillAvailable)
    }

    @Test
    fun whenInternalTestUserFalseThenGetCredentialsWithDomainReturnsEmpty() = runTest {
        setupTestee(isInternalUser = false, canAccessSecureStorage = true)
        val url = "example.com"
        storeCredentials(1, url, "username", "password")

        assertTrue(testee.getCredentials(url).isEmpty())
    }

    @Test
    fun whenInternalTestUserButCantAccessSecureStorageThenGetCredentialsWithDomainReturnsEmpty() = runTest {
        setupTestee(isInternalUser = true, canAccessSecureStorage = false)
        val url = "example.com"
        storeCredentials(1, url, "username", "password")

        assertTrue(testee.getCredentials(url).isEmpty())
    }

    @Test
    fun whenStoreEmptyThenNoMatch() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        val result = testee.containsCredentials("example.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsMatchingUsernameEntriesButNoneMatchingUrlThenNoMatch() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("foo.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsDomainButDifferentCredentialsThenUrlMatch() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "differentUsername", "differentPassword")
        assertUrlOnlyMatch(result)
    }

    @Test
    fun whenStoreContainsDomainAndUsernameButDifferentPassword() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "differentPassword")
        assertUsernameMatch(result)
    }

    @Test
    fun whenStoreContainsMatchingDomainAndUsernameAndPassword() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        storeCredentials(1, "https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "password")
        assertExactMatch(result)
    }

    @Test
    fun whenNoCredentialsForUrlStoredThenGetCredentialsReturnNothing() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        storeCredentials(1, "url.com", "username1", "password123")

        assertEquals(0, testee.getCredentials("https://example.com").size)
    }

    @Test
    fun whenNoCredentialsSavedThenGetAllCredentialsReturnNothing() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        assertEquals(0, testee.getAllCredentials().first().size)
    }

    @Test
    fun whenPasswordIsUpdatedForUrlThenUpdatedOnlyMatchingCredential() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        val url = "https://example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")
        storeCredentials(3, url, "username3", "password789")
        val credentials = LoginCredentials(
            domain = url,
            username = "username1",
            password = "newpassword",
            id = 1
        )

        testee.updateCredentials(url, credentials)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "username1", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
            this.assertHasLoginCredentials(url, "username2", "password456")
            this.assertHasLoginCredentials(url, "username3", "password789")
        }
    }

    @Test
    fun whenPasswordIsUpdatedThenUpdatedOnlyMatchingCredential() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        val url = "https://example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")
        storeCredentials(3, url, "username3", "password789")
        val credentials = LoginCredentials(
            domain = url,
            username = "username1",
            password = "newpassword",
            id = 1
        )

        testee.updateCredentials(credentials)

        testee.getCredentials(url).run {
            this.assertHasLoginCredentials(url, "username1", "newpassword", UPDATED_INITIAL_LAST_UPDATED)
            this.assertHasLoginCredentials(url, "username2", "password456")
            this.assertHasLoginCredentials(url, "username3", "password789")
        }
    }

    @Test
    fun whenSaveCredentialsThenReturnSavedOnGetCredentials() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        val url = "https://example.com"
        val credentials = LoginCredentials(
            domain = url,
            username = "username1",
            password = "password"
        )
        testee.saveCredentials(url, credentials)

        assertEquals(credentials.copy(lastUpdatedMillis = UPDATED_INITIAL_LAST_UPDATED), testee.getCredentials(url)[0])
    }

    @Test
    fun whenPasswordIsDeletedThenRemoveCredentialFromStore() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        val url = "https://example.com"
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
        setupTesteeWithAutofillEnabledAndAvailable()
        val url = "https://example.com"
        storeCredentials(1, url, "username1", "password123")
        storeCredentials(2, url, "username2", "password456")

        assertEquals(
            LoginCredentials(
                id = 1,
                domain = url,
                username = "username1",
                password = "password123",
                lastUpdatedMillis = DEFAULT_INITIAL_LAST_UPDATED
            ),
            testee.getCredentialsWithId(1)
        )
    }

    @Test
    fun whenNoCredentialStoredTheReturnNullOnGetCredentialsWithId() = runTest {
        setupTesteeWithAutofillEnabledAndAvailable()
        assertNull(testee.getCredentialsWithId(1))
    }

    private fun List<LoginCredentials>.assertHasNoLoginCredentials(
        url: String,
        username: String,
        password: String,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED
    ) {
        val result = this.filter {
            it.domain == url && it.username == username && it.password == password && it.lastUpdatedMillis == lastUpdatedTimeMillis
        }
        assertEquals(0, result.size)
    }

    private fun List<LoginCredentials>.assertHasLoginCredentials(
        url: String,
        username: String,
        password: String,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED
    ) {
        val result = this.filter {
            it.domain == url && it.username == username && it.password == password && it.lastUpdatedMillis == lastUpdatedTimeMillis
        }
        assertEquals(1, result.size)
    }

    private fun setupTesteeWithAutofillEnabledAndAvailable() {
        internalTestUserChecker = FakeInternalTestUserChecker(true)
        secureStore = FakeSecureStore(true)
        testee = SecureStoreBackedAutofillStore(secureStore, context, internalTestUserChecker, lastUpdatedTimeProvider)
    }

    private fun setupTestee(isInternalUser: Boolean, canAccessSecureStorage: Boolean) {
        internalTestUserChecker = FakeInternalTestUserChecker(isInternalUser)
        secureStore = FakeSecureStore(canAccessSecureStorage)
        testee = SecureStoreBackedAutofillStore(secureStore, context, internalTestUserChecker, lastUpdatedTimeProvider)
    }

    private fun assertNotMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected NoMatch but was %s", result), result is NoMatch)
    }

    private fun assertUrlOnlyMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected UrlOnlyMatch but was %s", result), result is UrlOnlyMatch)
    }

    private fun assertUsernameMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected UsernameMatch but was %s", result), result is UsernameMatch)
    }

    private fun assertExactMatch(result: ContainsCredentialsResult) {
        assertTrue(String.format("Expected ExactMatch but was %s", result), result is ExactMatch)
    }

    private suspend fun storeCredentials(
        id: Long,
        domain: String,
        username: String,
        password: String,
        lastUpdatedTimeMillis: Long = DEFAULT_INITIAL_LAST_UPDATED
    ) {
        val details = WebsiteLoginDetails(domain = domain, username = username, id = id, lastUpdatedMillis = lastUpdatedTimeMillis)
        val credentials = WebsiteLoginDetailsWithCredentials(details, password)
        secureStore.addWebsiteLoginDetailsWithCredentials(credentials)
    }

    private class FakeSecureStore(val canAccessSecureStorage: Boolean) : SecureStorage {

        private val credentials = mutableListOf<WebsiteLoginDetailsWithCredentials>()

        override suspend fun addWebsiteLoginDetailsWithCredentials(websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials): Long {
            credentials.add(websiteLoginDetailsWithCredentials)
            return credentials.size.toLong()
        }

        override suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>> {
            return flow {
                emit(
                    credentials.filter {
                        it.details.domain == domain
                    }.map {
                        it.details
                    }
                )
            }
        }

        override suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>> {
            return flow {
                emit(credentials.map { it.details })
            }
        }

        override suspend fun getWebsiteLoginDetailsWithCredentials(id: Long): WebsiteLoginDetailsWithCredentials? {
            return credentials.firstOrNull() { it.details.id == id }
        }

        override suspend fun websiteLoginDetailsWithCredentialsForDomain(domain: String): Flow<List<WebsiteLoginDetailsWithCredentials>> {
            return flow {
                emit(
                    credentials.filter {
                        it.details.domain == domain
                    }
                )
            }
        }

        override suspend fun websiteLoginDetailsWithCredentials(): Flow<List<WebsiteLoginDetailsWithCredentials>> {
            return flow {
                emit(credentials)
            }
        }

        override suspend fun updateWebsiteLoginDetailsWithCredentials(websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials) {
            credentials.indexOfFirst { it.details.id == websiteLoginDetailsWithCredentials.details.id }.also {
                credentials[it] = websiteLoginDetailsWithCredentials
            }
        }

        override suspend fun deleteWebsiteLoginDetailsWithCredentials(id: Long) {
            credentials.removeAll { it.details.id == id }
        }

        override fun canAccessSecureStorage(): Boolean = canAccessSecureStorage
    }

    private class FakeInternalTestUserChecker constructor(val expectedValueIsInternalTestUser: Boolean) : InternalTestUserChecker {
        override val isInternalTestUser: Boolean
            get() = expectedValueIsInternalTestUser

        override fun verifyVerificationErrorReceived(url: String) {}

        override fun verifyVerificationCompleted(url: String) {}
    }

    companion object {
        private const val DEFAULT_INITIAL_LAST_UPDATED = 200L
        private const val UPDATED_INITIAL_LAST_UPDATED = 10000L
    }
}
