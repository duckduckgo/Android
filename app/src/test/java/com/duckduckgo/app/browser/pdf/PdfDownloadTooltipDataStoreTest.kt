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

package com.duckduckgo.app.browser.pdf

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDownloadTooltipDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("pdf_download_tooltip") },
        )

    private val testee: PdfDownloadTooltipDataStore =
        SharedPreferencesPdfDownloadTooltipDataStore(
            testDataStore,
            coroutineRule.testDispatcherProvider,
        )

    companion object {
        val SHOWN_COUNT_KEY = intPreferencesKey("PDF_DOWNLOAD_TOOLTIP_SHOWN_COUNT")
    }

    @Test
    fun `when canShow called before any increment then returns true`() = runTest {
        assertTrue(testee.canShow())
    }

    @Test
    fun `when incrementShownCount called once then underlying key is one and canShow remains true`() = runTest {
        testee.incrementShownCount()
        assertEquals(1, testDataStore.data.firstOrNull()?.get(SHOWN_COUNT_KEY))
        assertTrue(testee.canShow())
    }

    @Test
    fun `when incrementShownCount called twice then canShow remains true`() = runTest {
        testee.incrementShownCount()
        testee.incrementShownCount()
        assertTrue(testee.canShow())
    }

    @Test
    fun `when incrementShownCount called three times then canShow returns false`() = runTest {
        testee.incrementShownCount()
        testee.incrementShownCount()
        testee.incrementShownCount()
        assertFalse(testee.canShow())
    }
}
