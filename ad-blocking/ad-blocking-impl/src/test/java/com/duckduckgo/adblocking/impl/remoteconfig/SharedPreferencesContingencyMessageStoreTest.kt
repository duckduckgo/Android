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

package com.duckduckgo.adblocking.impl.remoteconfig

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesContingencyMessageStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("ad_blocking_contingency_message_test") },
        )

    private val testee = SharedPreferencesContingencyMessageStore(testDataStore, coroutineRule.testScope)

    @Test
    fun whenNothingStoredThenShownIsFalse() = runTest {
        assertFalse(testee.shown.first())
    }

    @Test
    fun whenSetShownThenShownIsTrue() = runTest {
        testee.setShown()

        assertTrue(testee.shown.first())
    }

    @Test
    fun whenResetAfterSetShownThenShownIsFalse() = runTest {
        testee.setShown()
        assertTrue(testee.shown.first())

        testee.reset()

        assertFalse(testee.shown.first())
    }
}
