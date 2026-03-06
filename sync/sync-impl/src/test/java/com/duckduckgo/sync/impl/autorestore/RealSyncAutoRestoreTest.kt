/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.autorestore

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.sync.impl.SyncFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealSyncAutoRestoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val persistentStorage: PersistentStorage = mock()

    private lateinit var testee: RealSyncAutoRestore

    @Before
    fun setup() {
        testee = RealSyncAutoRestore(
            persistentStorage = persistentStorage,
            syncFeature = syncFeature,
            appScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFeatureFlagDisabledThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(false)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenFeatureFlagEnabledButNoStoredKeyThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveSuccess(value = null)

        assertFalse(testee.canRestore())
    }

    @Test
    fun whenFeatureFlagEnabledAndKeyExistsThenCanRestoreReturnsTrue() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveSuccess(value = "recovery_key_bytes")

        assertTrue(testee.canRestore())
    }

    @Test
    fun whenStorageRetrievalFailsThenCanRestoreReturnsFalse() = runTest {
        configureAutoRestoreEnabled(true)
        configureRetrieveFailure()

        assertFalse(testee.canRestore())
    }

    private fun configureAutoRestoreEnabled(enabled: Boolean) {
        syncFeature.syncAutoRestore().setRawStoredState(State(enable = enabled))
    }

    private suspend fun configureRetrieveSuccess(value: String?) {
        val bytes = value?.toByteArray(Charsets.UTF_8)
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.success(bytes))
    }

    private suspend fun configureRetrieveFailure() {
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.failure(RuntimeException("Retrieve failed")))
    }
}
