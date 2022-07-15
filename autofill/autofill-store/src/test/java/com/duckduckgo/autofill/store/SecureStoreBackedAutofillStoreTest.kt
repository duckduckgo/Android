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
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult.*
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SecureStoreBackedAutofillStoreTest {

    private val context: Context = getApplicationContext()
    private val secureStore = FakeSecureStore()
    private lateinit var testee: SecureStoreBackedAutofillStore
    private lateinit var internalTestUserChecker: FakeInternalTestUserChecker

    @Before
    fun setUp() {
        internalTestUserChecker = FakeInternalTestUserChecker(true)
        testee = SecureStoreBackedAutofillStore(secureStore, context, internalTestUserChecker)
    }

    @Test
    fun whenInternalTestUserTrueThenReturnAutofillEnabled() {
        setupTesteeWithInternalTestUserTrue()
        assertTrue(testee.autofillEnabled)
    }

    @Test
    fun whenInternalTestUserFalseThenReturnAutofillDisabled() {
        setupTesteeWithInternalTestUserFalse()
        assertFalse(testee.autofillEnabled)
    }

    @Test
    fun whenStoreEmptyThenNoMatch() = runTest {
        setupTesteeWithInternalTestUserTrue()
        val result = testee.containsCredentials("example.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsMatchingUsernameEntriesButNoneMatchingUrlThenNoMatch() = runTest {
        setupTesteeWithInternalTestUserTrue()
        storeCredentials("https://example.com", "username", "password")
        val result = testee.containsCredentials("foo.com", "username", "password")
        assertNotMatch(result)
    }

    @Test
    fun whenStoreContainsDomainButDifferentCredentialsThenUrlMatch() = runTest {
        setupTesteeWithInternalTestUserTrue()
        storeCredentials("https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "differentUsername", "differentPassword")
        assertUrlOnlyMatch(result)
    }

    @Test
    fun whenStoreContainsDomainAndUsernameButDifferentPassword() = runTest {
        setupTesteeWithInternalTestUserTrue()
        storeCredentials("https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "differentPassword")
        assertUsernameMatch(result)
    }

    @Test
    fun whenStoreContainsMatchingDomainAndUsernameAndPassword() = runTest {
        setupTesteeWithInternalTestUserTrue()
        storeCredentials("https://example.com", "username", "password")
        val result = testee.containsCredentials("example.com", "username", "password")
        assertExactMatch(result)
    }

    private fun setupTesteeWithInternalTestUserTrue() {
        internalTestUserChecker = FakeInternalTestUserChecker(true)
        testee = SecureStoreBackedAutofillStore(secureStore, context, internalTestUserChecker)
    }

    private fun setupTesteeWithInternalTestUserFalse() {
        internalTestUserChecker = FakeInternalTestUserChecker(false)
        testee = SecureStoreBackedAutofillStore(secureStore, context, internalTestUserChecker)
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
        domain: String,
        username: String,
        password: String
    ) {
        val details = WebsiteLoginDetails(domain, username)
        val credentials = WebsiteLoginDetailsWithCredentials(details, password)
        secureStore.addWebsiteLoginDetailsWithCredentials(credentials)
    }

    private class FakeSecureStore : SecureStorage {

        private val credentials = mutableMapOf<String, WebsiteLoginDetailsWithCredentials>()

        override suspend fun addWebsiteLoginDetailsWithCredentials(websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials) {
            credentials[websiteLoginDetailsWithCredentials.details.domain!!] = websiteLoginDetailsWithCredentials
        }

        override suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>> {
            return flow {
                emit(credentials[domain]?.let { listOf(it.details) } ?: emptyList())
            }
        }

        override suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>> {
            return flow {
                emit(credentials.values.map { it.details })
            }
        }

        override suspend fun getWebsiteLoginDetailsWithCredentials(id: Int): WebsiteLoginDetailsWithCredentials {
            return credentials.values.first { it.details.id == id }
        }

        override suspend fun websiteLoginDetailsWithCredentialsForDomain(domain: String): Flow<List<WebsiteLoginDetailsWithCredentials>> {
            return flow {
                emit(credentials[domain]?.let { listOf(it) } ?: emptyList())
            }
        }

        override suspend fun websiteLoginDetailsWithCredentials(): Flow<List<WebsiteLoginDetailsWithCredentials>> {
            return flow {
                emit(credentials.values.toList())
            }
        }

        override suspend fun updateWebsiteLoginDetailsWithCredentials(websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials) {
            credentials[websiteLoginDetailsWithCredentials.details.domain!!] = websiteLoginDetailsWithCredentials
        }

        override suspend fun deleteWebsiteLoginDetailsWithCredentials(id: Int) {
            credentials.values.removeAll { it.details.id == id }
        }

        override fun canAccessSecureStorage(): Boolean = true
    }

    private class FakeInternalTestUserChecker constructor(val expectedValueIsInternalTestUser: Boolean) : InternalTestUserChecker {
        override val isInternalTestUser: Boolean
            get() = expectedValueIsInternalTestUser

        override fun verifyVerificationErrorReceived(url: String) {}

        override fun verifyVerificationCompleted(url: String) {}
    }
}
