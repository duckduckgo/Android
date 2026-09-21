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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HighUsageModelNoticeDismissalStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: HighUsageModelNoticeDismissalStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("high_usage_notice_${UUID.randomUUID()}") },
        )
        testee = HighUsageModelNoticeDismissalStore(dataStore)
    }

    @Test
    fun whenDataStoreReadFailsThenDismissedModelIdsAreEmpty() = runTest {
        val failingStore: DataStore<Preferences> = mock()
        whenever(failingStore.data).thenReturn(flow { throw IOException("corrupt") })

        assertEquals(emptySet<String>(), HighUsageModelNoticeDismissalStore(failingStore).dismissedModelIds.first())
    }

    @Test(expected = IllegalStateException::class)
    fun whenDataStoreFailsWithNonIoErrorThenItPropagates() = runTest {
        val failingStore: DataStore<Preferences> = mock()
        whenever(failingStore.data).thenReturn(flow { throw IllegalStateException("bug") })

        HighUsageModelNoticeDismissalStore(failingStore).dismissedModelIds.first()
    }

    @Test
    fun whenNothingWasDismissedThenDismissedModelIdsAreEmpty() = runTest {
        assertEquals(emptySet<String>(), testee.dismissedModelIds.first())
    }

    @Test
    fun whenModelIsDismissedThenItsIdIsStored() = runTest {
        testee.dismiss("claude-opus-4-8")

        assertEquals(setOf("claude-opus-4-8"), testee.dismissedModelIds.first())
    }

    @Test
    fun whenAnotherModelIsDismissedThenExistingIdsArePreserved() = runTest {
        testee.dismiss("existing-model")

        testee.dismiss("claude-opus-4-8")

        assertEquals(
            setOf("existing-model", "claude-opus-4-8"),
            testee.dismissedModelIds.first(),
        )
    }

    @Test
    fun whenModelIsDismissedTwiceThenItsIdIsStoredOnce() = runTest {
        testee.dismiss("claude-opus-4-8")

        testee.dismiss("claude-opus-4-8")

        assertEquals(setOf("claude-opus-4-8"), testee.dismissedModelIds.first())
    }
}
