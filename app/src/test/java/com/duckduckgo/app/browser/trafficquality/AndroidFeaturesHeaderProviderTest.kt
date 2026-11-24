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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.remote.RealAndroidFeaturesHeaderProvider
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidFeaturesHeaderProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val mockFireDataStore: FireDataStore = mock()

    @Test
    fun whenAutoDeleteHasClearOptionsThenAdelEnabledIsTrue() {
        val testee = createTestee(automaticClearOptions = MutableStateFlow(setOf(FireClearOption.TABS, FireClearOption.DATA)))

        assertEquals("adel_enabled=true", testee.provide())
    }

    @Test
    fun whenAutoDeleteOnlyClearsTabsThenAdelEnabledIsTrue() {
        val testee = createTestee(automaticClearOptions = MutableStateFlow(setOf(FireClearOption.TABS)))

        assertEquals("adel_enabled=true", testee.provide())
    }

    @Test
    fun whenAutoDeleteHasNoClearOptionsThenAdelEnabledIsFalse() {
        val testee = createTestee(automaticClearOptions = MutableStateFlow(emptySet()))

        assertEquals("adel_enabled=false", testee.provide())
    }

    @Test
    fun whenAutoDeleteSettingChangesThenNextValueReflectsIt() {
        val options = MutableStateFlow<Set<FireClearOption>>(emptySet())
        val testee = createTestee(automaticClearOptions = options)
        assertEquals("adel_enabled=false", testee.provide())

        options.value = setOf(FireClearOption.DATA)

        assertEquals("adel_enabled=true", testee.provide())
    }

    @Test
    fun whenAutoDeleteStateNotLoadedYetThenValueReadDirectly() {
        whenever(mockFireDataStore.getAutomaticClearOptionsFlow()).thenReturn(emptyFlow())
        val testee = RealAndroidFeaturesHeaderProvider(
            mockFireDataStore,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
        )
        mockAutomaticClearOptions(setOf(FireClearOption.DATA))

        assertEquals("adel_enabled=true", testee.provide())
    }

    private fun createTestee(automaticClearOptions: Flow<Set<FireClearOption>>): RealAndroidFeaturesHeaderProvider {
        whenever(mockFireDataStore.getAutomaticClearOptionsFlow()).thenReturn(automaticClearOptions)
        return RealAndroidFeaturesHeaderProvider(
            mockFireDataStore,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    private fun mockAutomaticClearOptions(options: Set<FireClearOption>) {
        runBlocking { whenever(mockFireDataStore.getAutomaticClearOptions()).thenReturn(options) }
    }
}
