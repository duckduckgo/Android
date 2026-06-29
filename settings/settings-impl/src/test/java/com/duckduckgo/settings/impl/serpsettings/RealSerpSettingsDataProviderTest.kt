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

package com.duckduckgo.settings.impl.serpsettings

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeSerpSettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RealSerpSettingsDataProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dataStore = FakeSerpSettingsDataStore()

    private val testee = RealSerpSettingsDataProvider(
        serpSettingsDataStore = dataStore,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenNoSettingsStoredThenSettingIsNull() = runTest {
        assertNull(testee.observeSetting("kbe").first())
    }

    @Test
    fun whenKeyPresentInBlobThenReturnsItsValue() = runTest {
        dataStore.setSerpSettings("""{"kbe":"3","ko":"1"}""")

        assertEquals("3", testee.observeSetting("kbe").first())
    }

    @Test
    fun whenKeyAbsentFromBlobThenSettingIsNull() = runTest {
        dataStore.setSerpSettings("""{"ko":"1"}""")

        assertNull(testee.observeSetting("kbe").first())
    }

    @Test
    fun whenBlobIsEmptyObjectThenSettingIsNull() = runTest {
        dataStore.setSerpSettings("{}")

        assertNull(testee.observeSetting("kbe").first())
    }

    @Test
    fun whenBlobIsMalformedThenSettingIsNull() = runTest {
        dataStore.setSerpSettings("not-json")

        assertNull(testee.observeSetting("kbe").first())
    }

    @Test
    fun whenSetSettingOnEmptyStoreThenCreatesBlobWithKey() = runTest {
        testee.setSetting("kbe", "2")

        assertEquals("2", testee.observeSetting("kbe").first())
    }

    @Test
    fun whenSetSettingThenMergesWithExistingKeys() = runTest {
        dataStore.setSerpSettings("""{"ko":"1"}""")

        testee.setSetting("kbe", "3")

        assertEquals("3", testee.observeSetting("kbe").first())
        assertEquals("1", testee.observeSetting("ko").first())
    }

    @Test
    fun whenSetSettingThenOverwritesExistingValue() = runTest {
        dataStore.setSerpSettings("""{"kbe":"0"}""")

        testee.setSetting("kbe", "3")

        assertEquals("3", testee.observeSetting("kbe").first())
    }

    @Test
    fun whenSetSettingOnMalformedBlobThenReplacesWithValidObject() = runTest {
        dataStore.setSerpSettings("not-json")

        testee.setSetting("kbe", "3")

        assertEquals("3", testee.observeSetting("kbe").first())
    }
}
