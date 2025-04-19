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

package com.duckduckgo.autofill.sync.persister

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.FakePasswordStoreEventPlugin
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.CredentialsFixtures.amazonCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.toLoginCredentialEntryResponse
import com.duckduckgo.autofill.sync.CredentialsFixtures.toWebsiteLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.autofill.sync.CredentialsSync
import com.duckduckgo.autofill.sync.CredentialsSyncMapper
import com.duckduckgo.autofill.sync.CredentialsSyncMetadata
import com.duckduckgo.autofill.sync.FakeCredentialsSyncStore
import com.duckduckgo.autofill.sync.FakeCrypto
import com.duckduckgo.autofill.sync.SyncFakeSecureStorage
import com.duckduckgo.autofill.sync.credentialsSyncEntries
import com.duckduckgo.autofill.sync.inMemoryAutofillDatabase
import com.duckduckgo.autofill.sync.provider.CredentialsSyncLocalValidationFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CredentialsLocalWinsStrategyTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

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

    private val testee = CredentialsLocalWinsStrategy(
        credentialsSync = credentialsSync,
        credentialsSyncMapper = CredentialsSyncMapper(FakeCrypto()),
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenNoLocalEntitiesThenAllRemoteEntitiesStored() = runTest {
        givenLocalCredentials()
        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse(),
                spotifyCredentials.toLoginCredentialEntryResponse(),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )

        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(2, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") == twitterCredentials.id)
        assertTrue(credentialsSyncMetadata.getLocalId("2") == spotifyCredentials.id)
    }

    @Test
    fun whenRemoteAreMoreRecentThenLocalWins() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )

        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
                spotifyCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )
        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(2, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") != null)
        assertTrue(credentialsSyncMetadata.getLocalId("2") != null)
        assertTrue(secureStorage.getWebsiteLoginDetailsWithCredentials(1L)!!.details.domainTitle == twitterCredentials.domainTitle)
        assertTrue(secureStorage.getWebsiteLoginDetailsWithCredentials(2L)!!.details.domainTitle == spotifyCredentials.domainTitle)
    }

    @Test
    fun whenLocalIsMoreRecentThenLocalWins() = runTest {
        givenLocalCredentials(
            twitterCredentials.copy(lastUpdatedMillis = 1689592358516),
            spotifyCredentials.copy(lastUpdatedMillis = 1689592358516),
        )

        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
                spotifyCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )
        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(2, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") != null)
        assertTrue(credentialsSyncMetadata.getLocalId("2") != null)
        assertTrue(secureStorage.getWebsiteLoginDetailsWithCredentials(1L)!!.details.domainTitle == twitterCredentials.domainTitle)
        assertTrue(secureStorage.getWebsiteLoginDetailsWithCredentials(2L)!!.details.domainTitle == spotifyCredentials.domainTitle)
    }

    @Test
    fun whenLocalHasMoreCredentialsThenNoChanges() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
            amazonCredentials,
        )

        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
                spotifyCredentials.toLoginCredentialEntryResponse().copy(title = "NewTitle"),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )
        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(3, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") == 1L)
        assertTrue(credentialsSyncMetadata.getLocalId("2") == 2L)
        assertTrue(credentialsSyncMetadata.getLocalId("3") == 3L)
    }

    @Test
    fun whenRemoteHasMoreCredentialsThenKeepExistingAndAddNewCredentials() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )
        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse(),
                spotifyCredentials.toLoginCredentialEntryResponse(),
                amazonCredentials.toLoginCredentialEntryResponse(),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )

        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(3, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") == 1L)
        assertTrue(credentialsSyncMetadata.getLocalId("2") == 2L)
        assertTrue(credentialsSyncMetadata.getLocalId("3") == 3L)
    }

    @Test
    fun whenRemoteIsEmptyThenNoChanges() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )
        val remoteCredentials = credentialsSyncEntries(
            entries = emptyList(),
            last_modified = "2022-08-30T00:00:00Z",
        )

        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(2, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") == 1L)
        assertTrue(credentialsSyncMetadata.getLocalId("2") == 2L)
    }

    @Test
    fun whenRemoteMoreRecentAndDeletedThenNoChanges() = runTest {
        givenLocalCredentials(
            twitterCredentials,
            spotifyCredentials,
        )
        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse().copy(deleted = "1"),
                spotifyCredentials.toLoginCredentialEntryResponse().copy(deleted = "1"),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )

        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(2, storedValues.count())
        assertTrue(credentialsSyncMetadata.getLocalId("1") == 1L)
        assertTrue(credentialsSyncMetadata.getLocalId("2") == 2L)
    }

    @Test
    fun whenServerReturnsDeletedEntityThenDoNotSavecredentials() = runTest {
        val remoteCredentials = credentialsSyncEntries(
            entries = listOf(
                twitterCredentials.toLoginCredentialEntryResponse().copy(deleted = "1"),
                spotifyCredentials.toLoginCredentialEntryResponse().copy(deleted = "1"),
            ),
            last_modified = "2022-08-30T00:00:00Z",
        )

        val result = testee.processEntries(remoteCredentials, "2022-08-30T00:00:00Z")

        assertTrue(result is Success)
        val storedValues = secureStorage.websiteLoginDetailsWithCredentials().first()
        assertEquals(0, storedValues.count())
    }

    private suspend fun givenLocalCredentials(vararg credentials: LoginCredentials) {
        credentials.forEach {
            secureStorage.addWebsiteLoginDetailsWithCredentials(it.toWebsiteLoginCredentials())
            credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity(it.id!!.toString(), it.id!!, null, null))
        }
    }
}
