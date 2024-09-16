/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.webcompat.impl

import com.duckduckgo.webcompat.store.WebCompatEntity
import com.duckduckgo.webcompat.store.WebCompatRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WebCompatFeaturePluginTest {
    lateinit var testee: WebCompatFeaturePlugin

    private val mockWebCompatRepository: WebCompatRepository = mock()

    @Before
    fun before() {
        testee = WebCompatFeaturePlugin(mockWebCompatRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchWebCompatThenReturnFalse() {
        WebCompatFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesWebCompatThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesWebCompatThenUpdateAll() {
        testee.store(FEATURE_NAME_VALUE, JSON_STRING)
        val captor = argumentCaptor<WebCompatEntity>()
        verify(mockWebCompatRepository).updateAll(captor.capture())
        assertEquals(JSON_STRING, captor.firstValue.json)
    }

    companion object {
        private val FEATURE_NAME = WebCompatFeatureName.WebCompat
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
