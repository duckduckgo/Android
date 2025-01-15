/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.navigatorinterface

import com.duckduckgo.contentscopescripts.impl.features.navigatorinterface.store.NavigatorInterfaceEntity
import com.duckduckgo.contentscopescripts.impl.features.navigatorinterface.store.NavigatorInterfaceRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class NavigatorInterfaceFeaturePluginTest {
    private lateinit var testee: NavigatorInterfaceFeaturePlugin

    private val mockNavigatorInterfaceRepository: NavigatorInterfaceRepository = mock()

    @Before
    fun before() {
        testee = NavigatorInterfaceFeaturePlugin(mockNavigatorInterfaceRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchNavigatorInterfaceThenReturnFalse() {
        NavigatorInterfaceFeatureName.entries.filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesNavigatorInterfaceThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesNavigatorInterfaceThenUpdateAll() {
        testee.store(FEATURE_NAME_VALUE, JSON_STRING)
        val captor = argumentCaptor<NavigatorInterfaceEntity>()
        verify(mockNavigatorInterfaceRepository).updateAll(captor.capture())
        assertEquals(JSON_STRING, captor.firstValue.json)
    }

    companion object {
        private val FEATURE_NAME = NavigatorInterfaceFeatureName.NavigatorInterface
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
