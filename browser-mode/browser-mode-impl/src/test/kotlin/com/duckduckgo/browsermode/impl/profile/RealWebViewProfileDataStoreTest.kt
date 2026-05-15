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

package com.duckduckgo.browsermode.impl.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealWebViewProfileDataStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var store: DataStore<Preferences>
    private lateinit var testee: RealWebViewProfileDataStore

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        testee = RealWebViewProfileDataStore(store)
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun `default index per mode is 0`() = runTest {
        assertEquals(0, testee.getProfileIndex(BrowserMode.REGULAR))
        assertEquals(0, testee.getProfileIndex(BrowserMode.FIRE))
    }

    @Test
    fun `incrementing regular advances only regular index`() = runTest {
        val next = testee.incrementProfileIndex(BrowserMode.REGULAR)

        assertEquals(1, next)
        assertEquals(1, testee.getProfileIndex(BrowserMode.REGULAR))
        assertEquals(0, testee.getProfileIndex(BrowserMode.FIRE))
    }

    @Test
    fun `incrementing fire advances only fire index`() = runTest {
        testee.incrementProfileIndex(BrowserMode.FIRE)
        val next = testee.incrementProfileIndex(BrowserMode.FIRE)

        assertEquals(2, next)
        assertEquals(0, testee.getProfileIndex(BrowserMode.REGULAR))
        assertEquals(2, testee.getProfileIndex(BrowserMode.FIRE))
    }
}
