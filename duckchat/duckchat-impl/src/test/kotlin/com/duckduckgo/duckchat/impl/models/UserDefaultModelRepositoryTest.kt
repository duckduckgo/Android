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

package com.duckduckgo.duckchat.impl.models

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("DenyListedApi")
class UserDefaultModelRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = dispatcher)
    private val settings = MutableStateFlow<DuckAiBridgeSettingEntity?>(null)
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage> = mock()
    private val regularStorage: DuckAiBridgeStorage = mock()
    private val regularSettings: DuckAiBridgeSettingsDao = mock()
    private val setGates: List<(Boolean) -> Unit> = listOf(
        { enabled -> feature.resetModelForNewChats().setRawStoredState(State(enable = enabled)) },
        { enabled -> feature.userDefaultModel().setRawStoredState(State(enable = enabled)) },
        { enabled -> feature.duckAiNativeStorage().setRawStoredState(State(enable = enabled)) },
    )

    private val repository = UserDefaultModelRepository(lazy { storageProvider }, feature)

    init {
        setGates.forEach { it(true) }
        whenever(storageProvider.forMode(BrowserMode.REGULAR)).thenReturn(regularStorage)
        whenever(regularStorage.settings).thenReturn(regularSettings)
        whenever(regularSettings.observe(DEFAULT_MODEL_KEY)).thenReturn(settings)
    }

    @Test
    fun whenEnabledObservesDistinctDecodedDefaultsAndDeletionFromRegularStorage() = runTest(dispatcher) {
        assertTrue(repository.isUserDefaultEnabled())
        repository.observe().test {
            assertNull(awaitItem())

            settings.value = entry("\"gpt-6-luna\"")
            assertEquals(UserDefaultModel("gpt-6-luna"), awaitItem())

            settings.value = entry(""" "gpt-6-\u006cuna" """)
            runCurrent()
            expectNoEvents()

            settings.value = entry("\"claude-sonnet\"")
            assertEquals(UserDefaultModel("claude-sonnet"), awaitItem())

            settings.value = null
            assertNull(awaitItem())
        }
        verify(storageProvider).forMode(BrowserMode.REGULAR)
    }

    @Test
    fun malformedAndBlankValuesAreTreatedAsNoUserDefault() = runTest(dispatcher) {
        val unusableValues = listOf(
            "gpt-6-luna",
            "",
            " ",
            "\"\"",
            "\" \"",
            "\"\t\"",
            "\"model\nname\"",
            """ "model\'name" """,
            "'gpt-6-luna'",
            "\"gpt-6-luna\" \"second\"",
            "\"gpt-6-luna",
            "42",
            "true",
            "null",
            "[]",
            "{}",
        )
        for (rawValue in unusableValues) {
            settings.value = entry(rawValue)
            assertNull("Stored value: $rawValue", repository.observe().first())
        }

        settings.value = null
        assertNull(repository.observe().first())
    }

    @Test
    fun anyDisabledConditionLeavesStorageUntouched() = runTest(dispatcher) {
        for (setGate in setGates) {
            setGate(false)

            assertFalse(repository.isUserDefaultEnabled())
            assertNull(repository.observe().first())

            setGate(true)
        }
        verifyNoInteractions(storageProvider)
    }

    @Test
    fun disablingAnyConditionCancelsStorageObservationAndReenablingResumesIt() = runTest(dispatcher) {
        settings.value = entry("\"gpt-6-luna\"")
        repository.observe().test {
            assertEquals(UserDefaultModel("gpt-6-luna"), awaitItem())

            for (setGate in setGates) {
                setGate(false)

                assertNull(awaitItem())
                assertEquals(0, settings.subscriptionCount.value)

                setGate(true)

                assertEquals(UserDefaultModel("gpt-6-luna"), awaitItem())
            }
        }
    }

    @Test
    fun reenablingEmitsDeletionThatHappenedWhileDisabled() = runTest(dispatcher) {
        settings.value = entry("\"gpt-6-luna\"")
        repository.observe().test {
            assertEquals(UserDefaultModel("gpt-6-luna"), awaitItem())

            feature.duckAiNativeStorage().setRawStoredState(State(enable = false))
            assertNull(awaitItem())
            settings.value = null

            feature.duckAiNativeStorage().setRawStoredState(State(enable = true))
            assertNull(awaitItem())
        }
    }

    private fun entry(value: String) = DuckAiBridgeSettingEntity(key = DEFAULT_MODEL_KEY, value = value)

    private companion object {
        const val DEFAULT_MODEL_KEY = "defaultDuckaiModel"
    }
}
