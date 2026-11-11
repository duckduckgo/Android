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

package com.duckduckgo.devicemigration.impl

import android.content.SharedPreferences
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_TRANSFER_ALLOWED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.LOCAL_FILE
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class TransferAllowedRecorderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val prefsByName = mutableMapOf<String, SharedPreferences>()
    private val provider = object : SharedPreferencesProvider by FakeSharedPreferencesProvider() {
        override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences =
            prefsByName.getOrPut(name) { InMemorySharedPreferences() }
    }
    private val feature = FakeFeatureToggleFactory.create(DeviceMigrationFeature::class.java)
    private val local get() = provider.getSharedPreferences(LOCAL_FILE)

    private val testee = TransferAllowedRecorder(
        markerStore = DeviceMigrationMarkerStore(provider),
        deviceMigrationFeature = feature,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenFlagEnabledThenStartRecordsTransferAllowed() {
        feature.self().setRawStoredState(State(enable = true))

        testee.onCreate(mock())

        assertTrue(local.getBoolean(KEY_TRANSFER_ALLOWED, false))
    }

    @Test
    fun whenFlagDisabledThenStartRecordsTransferNotAllowed() {
        feature.self().setRawStoredState(State(enable = false))

        testee.onCreate(mock())

        assertFalse(local.getBoolean(KEY_TRANSFER_ALLOWED, true))
    }

    @Test
    fun whenConfigDownloadTurnsFlagOffThenRecordFollows() {
        feature.self().setRawStoredState(State(enable = true))
        testee.onCreate(mock())

        feature.self().setRawStoredState(State(enable = false))
        testee.onPrivacyConfigDownloaded()

        assertFalse(local.getBoolean(KEY_TRANSFER_ALLOWED, true))
    }
}
