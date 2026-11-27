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

package com.duckduckgo.duckchat.impl.clienttools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermission.ALWAYS_ALLOW
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermission.NEVER_ALLOW
import com.duckduckgo.duckchat.impl.nativeinput.footer.FailingPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealClientToolPermissionStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: RealClientToolPermissionStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("client_tool_permissions_${UUID.randomUUID()}") },
        )
        testee = RealClientToolPermissionStore(dataStore)
    }

    @Test
    fun whenNothingStoredThenGetIsNull() = runTest {
        assertNull(testee.get("listOpenTabs"))
    }

    @Test
    fun whenChoiceSetThenGetReturnsIt() = runTest {
        testee.set("listOpenTabs", ALWAYS_ALLOW)
        testee.set("openTab", NEVER_ALLOW)

        assertEquals(ALWAYS_ALLOW, testee.get("listOpenTabs"))
        assertEquals(NEVER_ALLOW, testee.get("openTab"))
    }

    @Test
    fun whenChoiceChangedThenLatestWins() = runTest {
        testee.set("listOpenTabs", ALWAYS_ALLOW)
        testee.set("listOpenTabs", NEVER_ALLOW)

        assertEquals(NEVER_ALLOW, testee.get("listOpenTabs"))
    }

    @Test
    fun whenOneToolClearedThenOthersRemain() = runTest {
        testee.set("listOpenTabs", ALWAYS_ALLOW)
        testee.set("openTab", NEVER_ALLOW)

        testee.clear("listOpenTabs")

        assertNull(testee.get("listOpenTabs"))
        assertEquals(NEVER_ALLOW, testee.get("openTab"))
    }

    @Test
    fun whenAllClearedThenNothingRemains() = runTest {
        testee.set("listOpenTabs", ALWAYS_ALLOW)
        testee.set("openTab", NEVER_ALLOW)

        testee.clearAll()

        assertEquals(emptyMap<String, ClientToolPermission>(), testee.observe().first())
    }

    @Test
    fun whenObservedThenEmitsEveryStoredChoice() = runTest {
        testee.set("listOpenTabs", ALWAYS_ALLOW)
        testee.set("openTab", NEVER_ALLOW)

        assertEquals(mapOf("listOpenTabs" to ALWAYS_ALLOW, "openTab" to NEVER_ALLOW), testee.observe().first())
    }

    @Test
    fun whenStoredValueUnknownThenReadsAsNoChoice() = runTest {
        dataStore.edit { it[stringPreferencesKey("listOpenTabs")] = "ALLOW_ON_TUESDAYS" }
        testee.set("openTab", NEVER_ALLOW)

        assertNull(testee.get("listOpenTabs"))
        assertEquals(mapOf("openTab" to NEVER_ALLOW), testee.observe().first())
    }

    @Test
    fun whenDataStoreReadFailsThenGetIsNull() = runTest {
        assertNull(RealClientToolPermissionStore(FailingPreferencesDataStore()).get("listOpenTabs"))
    }
}
