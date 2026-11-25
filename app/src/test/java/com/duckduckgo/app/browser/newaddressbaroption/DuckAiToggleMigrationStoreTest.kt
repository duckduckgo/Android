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
package com.duckduckgo.app.browser.newaddressbaroption

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckAiToggleMigrationStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun dataStore(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { context.preferencesDataStoreFile(name) },
    )

    private val migrationDataStore = dataStore("duck_ai_toggle_migration_test")
    private val migrationStore = RealDuckAiToggleMigrationStore(migrationDataStore, coroutineRule.testDispatcherProvider)
    private val legacyDataStore = dataStore("new_address_bar_option_test")
    private val legacyStore = RealLegacyAddressBarOptionStore(legacyDataStore, coroutineRule.testDispatcherProvider)

    @Test
    fun `when nothing was stored then the migration was not evaluated`() = runTest {
        assertEquals(MigrationState.NotEvaluated, migrationStore.state())
    }

    @Test
    fun `when each state is stored then it reads back the same`() = runTest {
        listOf(MigrationState.Skipped, MigrationState.Migrated(atMillis = 1_700_000_000_000L), MigrationState.Reverted).forEach {
            migrationStore.setState(it)

            assertEquals(it, migrationStore.state())
        }
    }

    @Test
    fun `when a newer version stored a state this version does not know then reads it as unrecognized`() = runTest {
        migrationDataStore.edit { it[stringPreferencesKey("STATE")] = "something_new" }

        assertEquals(MigrationState.Unrecognized, migrationStore.state())
    }

    @Test
    fun `when the V1 picker file has nothing then it was not shown`() = runTest {
        assertFalse(legacyStore.wasShown())
    }

    @Test
    fun `when the V1 picker recorded it was shown then reads it`() = runTest {
        legacyDataStore.edit { it[booleanPreferencesKey("NEW_ADDRESS_BAR_OPTION_WAS_SHOWN")] = true }

        assertTrue(legacyStore.wasShown())
    }
}
