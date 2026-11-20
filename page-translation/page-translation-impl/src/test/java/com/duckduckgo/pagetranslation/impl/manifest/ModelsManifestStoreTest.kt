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

package com.duckduckgo.pagetranslation.impl.manifest

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
        temporaryFolder.root.resolve(MANIFEST_PREFERENCES_FILE_NAME)
    }

    @Test
    fun returnNullBeforeFirstWrite() = runTest {
        assertNull(createStore(preferences).readManifest())
    }

    @Test
    fun keepPreviousFileAndETagReadableWhenPreferenceCommitFails() = runTest {
        createStore(preferences).replaceIfSupported(ORIGINAL_MANIFEST, ORIGINAL_ETAG)

        val failingPreferences = object : DataStore<Preferences> by preferences {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return preferences.updateData { current ->
                    transform(current)
                    throw IOException(COMMIT_FAILURE_MESSAGE)
                }
            }
        }

        try {
            createStore(failingPreferences).replaceIfSupported(UPDATED_MANIFEST, UPDATED_ETAG)
            fail(EXPECTED_FAILURE_MESSAGE)
        } catch (_: IOException) {
            val reopened = createStore(preferences)

            assertEquals(ORIGINAL_BASE_URL, reopened.readManifest()!!.baseUrl)
            assertEquals(ORIGINAL_ETAG, reopened.getEtagForRequest())
        }
    }

    private fun createStore(dataStore: DataStore<Preferences>) =
        ModelsManifestStore({ temporaryFolder.root.resolve(MANIFEST_DIRECTORY_NAME) }, dataStore, coroutineRule.testDispatcherProvider)

    private companion object {
        const val MANIFEST_PREFERENCES_FILE_NAME = "manifest.preferences_pb"
        const val MANIFEST_DIRECTORY_NAME = "page-translation"
        const val ORIGINAL_MANIFEST = """{"schemaVersion":1,"baseUrl":"first","models":{}}"""
        const val UPDATED_MANIFEST = """{"schemaVersion":1,"baseUrl":"second","models":{}}"""
        const val ORIGINAL_BASE_URL = "first"
        const val ORIGINAL_ETAG = "\"old\""
        const val UPDATED_ETAG = "\"new\""
        const val COMMIT_FAILURE_MESSAGE = "Failed to commit preferences"
        const val EXPECTED_FAILURE_MESSAGE = "Expected a failed write"
    }
}
