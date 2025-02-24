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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.FakePasswordStoreEventPlugin
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.CredentialsFixtures.invalidCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.toLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.autofill.sync.provider.CredentialsSyncLocalValidationFeature
import com.duckduckgo.autofill.sync.provider.LoginCredentialEntry
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CredentialsSyncTest {

    private val db = inMemoryAutofillDatabase()
    private val secureStorage = SyncFakeSecureStorage()
    private val credentialsSyncStore = FakeCredentialsSyncStore()
    private val credentialsSyncMetadata = CredentialsSyncMetadata(db.credentialsSyncDao())
    private val credentialsSync = CredentialsSync(
        secureStorage,
        credentialsSyncStore,
        credentialsSyncMetadata,
        FakeCrypto(),
        FakeFeatureToggleFactory.create(CredentialsSyncLocalValidationFeature::class.java),
        FakePasswordStoreEventPlugin(),
    )

    @After fun after() = runBlocking {
        db.close()
    }

    @Test
    fun whenInitMetadataThenSetServerModifiedSinceToZero() = runTest {
        credentialsSync.initMetadata()

        assertEquals("0", credentialsSyncStore.serverModifiedSince)
        assertEquals("0", credentialsSyncStore.clientModifiedSince)
    }

    @Test
    fun whenInitMetadataThenCreateMetadataForAllEntitiesWithModifiedSince() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )
        credentialsSyncMetadata.clearAll()

        credentialsSync.initMetadata()

        assertEquals(2, credentialsSyncMetadata.getAllCredentials().size)
        assertEquals(2, credentialsSyncMetadata.getAllCredentials().size)
        assertNotNull(credentialsSyncMetadata.getAllCredentials().first().modified_at)
    }

    @Test
    fun whenGetUpdatesThenStartTimeUpdates() = runTest {
        credentialsSync.getUpdatesSince("0")

        assertNotNull(credentialsSyncStore.startTimeStamp)
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
    fun whenGetUpdatesSinceDateThenEntitiesWithModifiedAtNullNotReturned() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val updates = credentialsSync.getUpdatesSince("2022-08-30T00:00:00Z")

        assertTrue(updates.isEmpty())
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
    fun whenOnFirstWithInvalidCredentialsThenChangesDoesNotContainInvalidEntities() = runTest {
        givenLocalCredentials(
            invalidCredentials,
        )

        val syncChanges = credentialsSync.getUpdatesSince("0")

        assertTrue(syncChanges.isEmpty())
        assertTrue(credentialsSyncStore.invalidEntitiesIds.size == 1)
    }

    @Test
    fun whenNewCredentialsIsInvalidThenChangesDoesNotContainInvalidEntity() = runTest {
        givenLocalCredentials(
            spotifyCredentials,
            invalidCredentials.copy(lastUpdatedMillis = 1689592358516),
        )

        val syncChanges = credentialsSync.getUpdatesSince("2022-08-30T00:00:00Z")

        assertTrue(syncChanges.isEmpty())
        assertTrue(credentialsSyncStore.invalidEntitiesIds.size == 1)
    }

    @Test
    fun whenInvalidCredentialsPresentThenAlwaysRetryItemsAndUpdateInvalidList() = runTest {
        givenLocalCredentials(
            invalidCredentials,
            spotifyCredentials.copy(lastUpdatedMillis = 1689592358516),
        )
        credentialsSyncStore.invalidEntitiesIds = listOf(credentialsSyncMetadata.getSyncMetadata(invalidCredentials.id!!)!!.syncId)

        val syncChanges = credentialsSync.getUpdatesSince("2022-08-30T00:00:00Z")

        assertTrue(syncChanges.size == 1)
        assertTrue(syncChanges.first().title == spotifyCredentials.domainTitle)
        assertTrue(credentialsSyncStore.invalidEntitiesIds.size == 1)
    }

    @Test
    fun whenInvalidCredentialsThenReturnInvalidCredentials() = runTest {
        givenLocalCredentials(
            invalidCredentials,
        )
        credentialsSyncStore.invalidEntitiesIds = listOf(credentialsSyncMetadata.getSyncMetadata(invalidCredentials.id!!)!!.syncId)

        val invalidItems = credentialsSync.getInvalidCredentials()

        assertTrue(invalidItems.isNotEmpty())
        assertEquals(invalidCredentials, invalidItems.first())
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
    fun whenSaveCredentialsThenSaveCredentialAndSyncMetadata() = runTest {
        credentialsSync.saveCredential(twitterCredentials, "123")

        secureStorage.getWebsiteLoginDetailsWithCredentials(twitterCredentials.id!!)!!.toLoginCredentials().let {
            assertEquals(twitterCredentials, it)
        }
        credentialsSyncMetadata.getSyncMetadata(twitterCredentials.id!!).let { metadata ->
            assertEquals("123", metadata?.syncId)
            assertNull(metadata?.modified_at)
        }
    }

    @Test
    fun whenSaveCredentialsToExistingSyncIdThenSaveToAutofillStoreAndOverrideSyncId() = runTest {
        credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity("321", twitterCredentials.id!!, null, null))

        credentialsSync.saveCredential(twitterCredentials, "123")

        secureStorage.getWebsiteLoginDetailsWithCredentials(twitterCredentials.id!!)!!.toLoginCredentials().let {
            assertEquals(twitterCredentials, it)
        }
        credentialsSyncMetadata.getSyncMetadata(twitterCredentials.id!!).let { metadata ->
            assertEquals("123", metadata?.syncId)
            assertNull(metadata?.modified_at)
        }
        assertNull(credentialsSyncMetadata.getLocalId("321"))
    }

    @Test
    fun whenUpdateCredentialsThenUpdateAndSyncMetadata() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        credentialsSync.updateCredentials(twitterCredentials.copy(username = "new-username"), "123")

        secureStorage.getWebsiteLoginDetailsWithCredentials(twitterCredentials.id!!)!!.toLoginCredentials().let {
            assertEquals(twitterCredentials.copy(username = "new-username"), it)
        }
        credentialsSyncMetadata.getSyncMetadata(twitterCredentials.id!!).let { metadata ->
            assertEquals("123", metadata?.syncId)
            assertNull(metadata?.modified_at)
        }
    }

    @Test
    fun whenDeleteCredentialThenDeleteFromAutofillStoreAndSyncMetadata() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        credentialsSync.deleteCredential(twitterCredentials.id!!)

        assertNull(secureStorage.getWebsiteLoginDetailsWithCredentials(twitterCredentials.id!!))
        assertNull(credentialsSyncMetadata.getSyncMetadata(twitterCredentials.id!!))
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
        credentials.forEach { credential ->
            val loginDetails = WebsiteLoginDetails(
                id = credential.id,
                domain = credential.domain,
                username = credential.username,
                domainTitle = credential.domainTitle,
                lastUpdatedMillis = credential.lastUpdatedMillis,
            )
            val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(
                details = loginDetails,
                password = credential.password,
                notes = credential.notes,
            )

            secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)
            with(credential.id!!) {
                val lastUpdatedIso = credential.lastUpdatedMillis?.let { DatabaseDateFormatter.parseMillisIso8601(it) }
                credentialsSyncMetadata.addOrUpdate(
                    CredentialsSyncMetadataEntity(
                        syncId = this.toString(),
                        localId = this,
                        deleted_at = null,
                        modified_at = lastUpdatedIso,
                    ),
                )
            }
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
