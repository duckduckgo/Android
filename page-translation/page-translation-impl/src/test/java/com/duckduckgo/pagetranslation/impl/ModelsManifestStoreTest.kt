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

package com.duckduckgo.pagetranslation.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ModelsManifestStoreTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val preferences = PreferenceDataStoreFactory.create(scope = coroutineRule.testScope) {
        temporaryFolder.root.resolve("manifest.preferences_pb")
    }

    @Test
    fun manifestIsNullBeforeFirstWrite() = runTest {
        assertNull(store(preferences).manifest())
    }

    @Test
    fun failedPreferenceCommitLeavesPreviousFileAndETagReadable() = runTest {
        store(preferences).replaceIfSupported("""{"schemaVersion":1,"baseUrl":"first","models":{}}""", "\"old\"")
        val failingPreferences = object : DataStore<Preferences> by preferences {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return preferences.updateData { current ->
                    transform(current)
                    throw IOException("Failed to commit preferences")
                }
            }
        }

        try {
            store(failingPreferences).replaceIfSupported("""{"schemaVersion":1,"baseUrl":"second","models":{}}""", "\"new\"")
            fail("Expected a failed write")
        } catch (_: IOException) {
            val reopened = store(preferences)
            assertEquals("first", reopened.manifest()!!.baseUrl)
            assertEquals("\"old\"", reopened.etagForRequest())
        }
    }

    private fun store(dataStore: DataStore<Preferences>) =
        ModelsManifestStore({ temporaryFolder.root.resolve("page-translation") }, dataStore, coroutineRule.testDispatcherProvider)
}
