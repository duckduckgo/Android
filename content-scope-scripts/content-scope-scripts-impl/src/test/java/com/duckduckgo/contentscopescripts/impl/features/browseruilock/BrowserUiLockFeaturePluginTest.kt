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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrowserUiLockFeaturePluginTest {
    private lateinit var testee: BrowserUiLockFeaturePlugin

    private val mockBrowserUiLockRepository: BrowserUiLockRepository = mock()

    @Before
    fun before() {
        testee = BrowserUiLockFeaturePlugin(mockBrowserUiLockRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchBrowserUiLockThenReturnFalse() {
        assertFalse(testee.store("otherFeature", JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesBrowserUiLockThenReturnTrue() {
        whenever(runBlocking { mockBrowserUiLockRepository.insertJsonData(JSON_STRING) }).thenReturn(true)
        assertTrue(testee.store(BROWSER_UI_LOCK_FEATURE_NAME, JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesBrowserUiLockThenInsertJsonData() {
        whenever(runBlocking { mockBrowserUiLockRepository.insertJsonData(JSON_STRING) }).thenReturn(true)
        testee.store(BROWSER_UI_LOCK_FEATURE_NAME, JSON_STRING)
        runBlocking { verify(mockBrowserUiLockRepository).insertJsonData(JSON_STRING) }
    }

    companion object {
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
