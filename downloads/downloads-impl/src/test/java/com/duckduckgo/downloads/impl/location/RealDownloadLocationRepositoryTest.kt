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

package com.duckduckgo.downloads.impl.location

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.downloads.api.CustomDownloadLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealDownloadLocationRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { context.preferencesDataStoreFile("downloads_location_test") },
    )

    private val repository = RealDownloadLocationRepository(dataStore)

    @Test
    fun whenNoLocationSavedThenGetCustomLocationReturnsNull() = runTest {
        assertNull(repository.getCustomLocation())
    }

    @Test
    fun whenLocationSavedThenItCanBeRetrieved() = runTest {
        val location = CustomDownloadLocation(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            displayName = "Download",
            pathLabel = "Internal Storage/Downloads",
        )

        repository.saveCustomLocation(location)

        assertEquals(location, repository.getCustomLocation())
    }

    @Test
    fun whenLocationClearedThenGetCustomLocationReturnsNull() = runTest {
        repository.saveCustomLocation(
            CustomDownloadLocation(
                treeUri = "content://tree",
                displayName = "Download",
                pathLabel = "Downloads",
            ),
        )

        repository.clearCustomLocation()

        assertNull(repository.getCustomLocation())
    }
}
