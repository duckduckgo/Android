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

package com.duckduckgo.autofill.sync.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.FakePasswordStoreEventPlugin
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.CredentialsFixtures
import com.duckduckgo.autofill.sync.CredentialsFixtures.toWebsiteLoginCredentials
import com.duckduckgo.autofill.sync.CredentialsSync
import com.duckduckgo.autofill.sync.CredentialsSyncMetadata
import com.duckduckgo.autofill.sync.FakeCredentialsSyncStore
import com.duckduckgo.autofill.sync.FakeCrypto
import com.duckduckgo.autofill.sync.SyncFakeSecureStorage
import com.duckduckgo.autofill.sync.inMemoryAutofillDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.sync.api.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class CredentialsSyncDataProviderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val db = inMemoryAutofillDatabase()
    private val secureStorage = SyncFakeSecureStorage()
    private val credentialsSyncMetadata = CredentialsSyncMetadata(db.credentialsSyncDao())
    private val credentialsSyncStore = FakeCredentialsSyncStore()
    private val credentialsSync = CredentialsSync(
        secureStorage,
        credentialsSyncStore,
        credentialsSyncMetadata,
        FakeCrypto(),
        FakeFeatureToggleFactory.create(CredentialsSyncLocalValidationFeature::class.java),
        FakePasswordStoreEventPlugin(),
    )
    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.flavor).thenReturn(BuildFlavor.PLAY)
    }

    @After fun after() = runBlocking {
        db.close()
    }

    private val testee = CredentialsSyncDataProvider(
        credentialsSyncStore = credentialsSyncStore,
        credentialsSync = credentialsSync,
        dispatchers = coroutineRule.testDispatcherProvider,
        appBuildConfig = appBuildConfig,
    )

    @Test
    fun whenInitialSyncAndNoDataThenReturnEmptyList() = runTest {
        val result = testee.getChanges()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenInitialSyncAndDataThenAllChanges() = runTest {
        givenLocalCredentials(
            CredentialsFixtures.twitterCredentials,
            CredentialsFixtures.spotifyCredentials,
        )

        val result = testee.getChanges()

        assertTrue(result.type == SyncableType.CREDENTIALS)
        assertTrue(result.jsonString.findOccurrences("id") == 2)
        assertTrue(result.modifiedSince is ModifiedSince.FirstSync)
    }

    @Test
    fun whenModifiedSinceExistsThenSendChangesWithServerTimeSince() = runTest {
        credentialsSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        credentialsSyncStore.serverModifiedSince = "2022-08-30T00:00:00Z"

        givenLocalCredentials(
            CredentialsFixtures.twitterCredentials,
            CredentialsFixtures.spotifyCredentials.copy(lastUpdatedMillis = 1689592358516),
        )

        val result = testee.getChanges()

        assertTrue(result.type == SyncableType.CREDENTIALS)
        assertTrue(result.jsonString.findOccurrences("id") == 1)
        assertTrue(result.modifiedSince == ModifiedSince.Timestamp("2022-08-30T00:00:00Z"))
    }

    @Test
    fun whenSendingDataThenStartTimeUpdated() = runTest {
        credentialsSyncStore.startTimeStamp = "0"
        givenLocalCredentials(
            CredentialsFixtures.twitterCredentials,
            CredentialsFixtures.spotifyCredentials,
        )

        testee.getChanges()

        assertTrue(credentialsSyncStore.startTimeStamp != "0")
    }

    @Test
    fun whenNullPropertyThenIncludeNullInJson() = runTest {
        credentialsSyncStore.serverModifiedSince = "0"

        givenLocalCredentials(
            CredentialsFixtures.twitterCredentials.copy(domainTitle = null),
        )

        val result = testee.getChanges()
        assertTrue(result.type == SyncableType.CREDENTIALS)
        assertTrue(result.jsonString.findOccurrences("\"title\":null") == 1)
    }

    private fun String.findOccurrences(regex: String): Int {
        val regex = regex.toRegex()
        val matches = regex.findAll(this)
        return matches.count()
    }

    private suspend fun givenLocalCredentials(vararg credentials: LoginCredentials) {
        credentials.forEach { credential ->
            secureStorage.addWebsiteLoginDetailsWithCredentials(credential.toWebsiteLoginCredentials())
            val lastUpdatedIso = credential.lastUpdatedMillis?.let { DatabaseDateFormatter.parseMillisIso8601(it) }
            credentialsSyncMetadata.addOrUpdate(
                CredentialsSyncMetadataEntity(
                    syncId = credential.id!!.toString(),
                    localId = credential.id!!,
                    deleted_at = null,
                    modified_at = lastUpdatedIso,
                ),
            )
        }
    }
}
