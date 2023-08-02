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

package com.duckduckgo.autofill.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.autofill.sync.provider.LoginCredentialEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class CredentialsSyncTest {

    private val db = inMemoryAutofillDatabase()
    private val secureStorage = FakeSecureStorage()
    private val autofillStore = FakeAutofillStore(secureStorage)
    private val credentialsSyncMetadata = CredentialsSyncMetadata(db.credentialsSyncDao())
    private val credentialsSync = CredentialsSync(autofillStore, secureStorage, credentialsSyncMetadata, FakeCrypto())

    @After fun after() = runBlocking {
        db.close()
    }

    @Test
    fun whenGetUpdatesSinceZeroTimeThenReturnAllContent() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val updates = credentialsSync.getUpdatesSince("0")

        assertTrue(updates.size == 2)
        assertUpdates(
            listOf(
                twitterCredentials.asLoginCredentialEntry(),
                spotifyCredentials.asLoginCredentialEntry(),
            ),
            updates,
        )
    }

    @Test
    fun whenGetUpdatesSinceDateThenReturnRecentUpdates() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials.copy(lastUpdatedMillis = 1689592358516),
        )

        val updates = credentialsSync.getUpdatesSince("2022-08-30T00:00:00Z")

        assertTrue(updates.size == 1)
        assertUpdates(
            listOf(spotifyCredentials.asLoginCredentialEntry()),
            updates,
        )
    }

    @Test
    fun whenUpdatesContainDeletedItemsThenReturnIncludeDeletedItemsInUpdate() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials.copy(lastUpdatedMillis = 1689592358516),
        )
        credentialsSyncMetadata.onEntityRemoved(twitterCredentials.id!!)

        val updates = credentialsSync.getUpdatesSince("2022-08-30T00:00:00Z")

        assertUpdates(
            listOf(
                spotifyCredentials.asLoginCredentialEntry(),
                twitterCredentials.asLoginCredentialEntry(deleted = true),
            ),
            updates,
        )
    }

    @Test
    fun whenGetCredentialWithSyncIdThenReturnCredentials() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val credential = credentialsSync.getCredentialWithSyncId(twitterCredentials.id!!.toString())

        assertEquals(twitterCredentials, credential)
    }

    @Test
    fun whenGetCredentialsWithSyncIdNotFoundThenReturnNull() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val credential = credentialsSync.getCredentialWithSyncId("not-found")

        assertNull(credential)
    }

    @Test
    fun whenGetCredentialsWithLocalIdThenReturnCredentials() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val credential = credentialsSync.getCredentialWithId(twitterCredentials.id!!)

        assertEquals(twitterCredentials, credential)
    }

    @Test
    fun whenGetCredentialsWithLocalIdNotFoundThenReturnNull() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val credential = credentialsSync.getCredentialWithId(1234)

        assertNull(credential)
    }

    @Test
    fun whenGetCredentialsForDomainThenReturnCredentials() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val credentials = credentialsSync.getCredentialsForDomain(twitterCredentials.domain!!)

        assertEquals(listOf(twitterCredentials), credentials)
    }

    @Test
    fun whenSaveCredentialsThenSaveCredentialAndSyncId() = runTest {
        credentialsSync.saveCredential(twitterCredentials, "123")

        autofillStore.getCredentialsWithId(twitterCredentials.id!!).let {
            assertEquals(twitterCredentials, it)
        }
        credentialsSyncMetadata.getSyncId(twitterCredentials.id!!).let {
            assertEquals("123", it)
        }
    }

    @Test
    fun whenSaveCredentialsToExistingSyncIdThenSaveToAutofillStoreAndOverrideSyncId() = runTest {
        credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity("321", twitterCredentials.id!!))

        credentialsSync.saveCredential(twitterCredentials, "123")

        autofillStore.getCredentialsWithId(twitterCredentials.id!!).let {
            assertEquals(twitterCredentials, it)
        }
        credentialsSyncMetadata.getSyncId(twitterCredentials.id!!).let {
            assertEquals("123", it)
        }
        assertNull(credentialsSyncMetadata.getLocalId("321"))
    }

    @Test
    fun whenUpdateCredentialsThenUpdateAndSyncId() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        credentialsSync.updateCredentials(twitterCredentials.copy(username = "new-username"), "123")

        autofillStore.getCredentialsWithId(twitterCredentials.id!!).let {
            assertEquals(twitterCredentials.copy(username = "new-username"), it)
        }
        credentialsSyncMetadata.getSyncId(twitterCredentials.id!!).let {
            assertEquals("123", it)
        }
    }

    @Test
    fun whenDeleteCredentialThenDeleteFromAutofillStoreAndSyncId() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        credentialsSync.deleteCredential(twitterCredentials.id!!)

        assertNull(autofillStore.getCredentialsWithId(twitterCredentials.id!!))
        credentialsSyncMetadata.getSyncId(twitterCredentials.id!!).let {
            assertNull(it)
        }
    }

    private fun assertUpdates(
        expected: List<LoginCredentialEntry>,
        updates: List<LoginCredentialEntry>,
    ) {
        assertEquals(expected.size, updates.size)

        expected.forEach {
            val update = updates.find { update -> update.id == it.id } ?: throw AssertionError("Expected update not found")
            if (it.deleted != null) {
                assertEquals("1", update.deleted)
                assertNull(update.domain)
                assertNull(update.username)
                assertNull(update.password)
                assertNull(update.title)
                assertNull(update.notes)
            } else {
                assertEquals(it.domain, update.domain)
                assertEquals(it.username, update.username)
                assertEquals(it.password, update.password)
                assertEquals(it.title, update.title)
                assertEquals(it.notes, update.notes)
                assertEquals(it.deleted, update.deleted)
            }
        }
    }

    private suspend fun givenLocalCredentials(vararg credentials: LoginCredentials) {
        credentials.forEach {
            autofillStore.saveCredentials(
                it.domain.orEmpty(),
                it,
            )
            credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity(it.id!!.toString(), it.id!!))
        }
    }

    private fun LoginCredentials.asLoginCredentialEntry(deleted: Boolean = false): LoginCredentialEntry {
        return LoginCredentialEntry(
            id = id.toString(),
            client_last_modified = DatabaseDateFormatter.parseMillisIso8601(lastUpdatedMillis ?: 0L),
            domain = domain,
            title = domainTitle,
            username = username,
            password = password,
            notes = notes,
            deleted = if (!deleted) null else "1",
        )
    }
}
