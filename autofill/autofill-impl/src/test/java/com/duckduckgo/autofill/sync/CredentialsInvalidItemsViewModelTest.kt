/*
 * Copyright (c) 2024 DuckDuckGo
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
import app.cash.turbine.test
import com.duckduckgo.autofill.impl.FakePasswordStoreEventPlugin
import com.duckduckgo.autofill.sync.CredentialsFixtures.invalidCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.provider.CredentialsSyncLocalValidationFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialsInvalidItemsViewModelTest {

    @get:Rule
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

    private val viewModel = CredentialsInvalidItemsViewModel(
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        crendentialsSyncRepository = credentialsSync,
    )

    @Test
    fun whenNoInvalidCredentialsThenWarningNotVisible() = runTest {
        viewModel.viewState().test {
            val awaitItem = awaitItem()
            assertFalse(awaitItem.warningVisible)
            assertEquals(0, awaitItem.invalidItemsSize)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInvalidCredentialsThenWarningVisible() = runTest {
        credentialsSync.saveCredential(invalidCredentials, "remote1")
        credentialsSync.saveCredential(spotifyCredentials, "remote2")
        credentialsSync.getUpdatesSince("0") // trigger sync so invalid credentials are detected

        viewModel.viewState().test {
            val awaitItem = awaitItem()
            assertTrue(awaitItem.warningVisible)
            assertEquals(1, awaitItem.invalidItemsSize)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
