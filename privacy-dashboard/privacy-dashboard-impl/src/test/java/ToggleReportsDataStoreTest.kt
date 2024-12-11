/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore
import com.duckduckgo.privacy.dashboard.impl.ToggleReportsFeature
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ToggleReportsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { context.preferencesDataStoreFile("toggle_reports") },
    )

    private val moshi = Moshi.Builder().build()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private lateinit var mockToggleReportsFeature: ToggleReportsFeature
    private lateinit var mockSelfToggle: Toggle

    private lateinit var testee: SharedPreferencesToggleReportsDataStore

    @Before
    fun setup() {
        mockToggleReportsFeature = mock(ToggleReportsFeature::class.java)
        mockSelfToggle = mock(Toggle::class.java)

        val defaultSettings = """
        {
            "dismissInterval": 1000000,
            "promptInterval": 1000000,
            "maxPromptCount": 3,
            "dismissLogicEnabled": true,
            "promptLimitLogicEnabled": true
        }
        """
        whenever(mockToggleReportsFeature.self()).thenReturn(mockSelfToggle)
        whenever(mockSelfToggle.getSettings()).thenReturn(defaultSettings)

        testee = SharedPreferencesToggleReportsDataStore(testDataStore, moshi, mockToggleReportsFeature, dispatcherProvider)
    }

    @Test
    fun whenDismissLogicAndPromptLimitLogicDisabledThenCanPromptReturnsTrue() = runTest {
        val customSettings = """
        {
            "dismissInterval": 1000000,
            "promptInterval": 1000000,
            "maxPromptCount": 3,
            "dismissLogicEnabled": false,
            "promptLimitLogicEnabled": false
        }
        """
        whenever(mockSelfToggle.getSettings()).thenReturn(customSettings)
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenNoStoredDismissalsOrSendsThenCanPromptReturnsTrue() = runTest {
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenDismissIntervalNotPassedThenCanPromptReturnsFalse() = runTest {
        testee.insertTogglePromptDismiss()
        assertFalse(testee.canPrompt())
    }

    @Test
    fun whenDismissIntervalPassedThenCanPromptReturnsTrue() = runTest {
        val customSettings = """
        {
            "dismissInterval": 0,
            "promptInterval": 1000000,
            "maxPromptCount": 3,
            "dismissLogicEnabled": true,
            "promptLimitLogicEnabled": true
        }
        """
        whenever(mockSelfToggle.getSettings()).thenReturn(customSettings)
        testee.insertTogglePromptDismiss()
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenPromptIntervalPassedThenCanPromptReturnsTrue() = runTest {
        val customSettings = """
        {
            "dismissInterval": 1000000,
            "promptInterval": 0,
            "maxPromptCount": 3,
            "dismissLogicEnabled": true,
            "promptLimitLogicEnabled": true
        }
        """
        whenever(mockSelfToggle.getSettings()).thenReturn(customSettings)
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenMaxPromptCountIsReachedThenCanPromptReturnsFalse() = runTest {
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
        testee.insertTogglePromptSend()
        assertFalse(testee.canPrompt())
    }

    @Test
    fun whenAllConditionsAreFavorableThenCanPromptReturnsTrue() = runTest {
        val customSettings = """
        {
            "dismissInterval": 0,
            "promptInterval": 0,
            "maxPromptCount": 5,
            "dismissLogicEnabled": true,
            "promptLimitLogicEnabled": true
        }
        """
        whenever(mockSelfToggle.getSettings()).thenReturn(customSettings)
        assertTrue(testee.canPrompt())
    }
}
