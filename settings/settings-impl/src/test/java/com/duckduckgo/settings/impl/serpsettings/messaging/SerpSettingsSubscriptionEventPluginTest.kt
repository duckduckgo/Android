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

package com.duckduckgo.settings.impl.serpsettings.messaging

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeSerpSettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SerpSettingsSubscriptionEventPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dataStore = FakeSerpSettingsDataStore()

    private val testee = SerpSettingsSubscriptionEventPlugin(
        serpSettingsDataStore = dataStore,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenBlobHasSettingsThenEmitsSerpSettingsNativeSettingsDidChangeSnapshot() = runTest {
        dataStore.setSerpSettings("""{"kbe":"0","kbj":"1"}""")

        val event = testee.getSubscriptionEventData()

        assertEquals("serpSettings", event.featureName)
        assertEquals("nativeSettingsDidChange", event.subscriptionName)
        assertEquals("0", event.params.getString("kbe"))
        assertEquals("1", event.params.getString("kbj"))
    }

    @Test
    fun whenNoBlobStoredThenEmitsEmptySnapshotNotNoNativeSettings() = runTest {
        val event = testee.getSubscriptionEventData()

        // The live channel always emits a full-state snapshot; noNativeSettings is a getNativeSettings-only form.
        assertFalse(event.params.has("noNativeSettings"))
        assertEquals(0, event.params.length())
    }

    @Test
    fun whenBlobMalformedThenEmitsEmptySnapshot() = runTest {
        dataStore.setSerpSettings("not-json")

        val event = testee.getSubscriptionEventData()

        assertFalse(event.params.has("noNativeSettings"))
        assertEquals(0, event.params.length())
    }
}
