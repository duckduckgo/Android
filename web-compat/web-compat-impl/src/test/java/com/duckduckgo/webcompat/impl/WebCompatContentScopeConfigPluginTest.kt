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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebCompatContentScopeConfigPluginTest {

    lateinit var testee: WebCompatContentScopeConfigPlugin

    private val mockWebCompatRepository: WebCompatRepository = mock()

    @Before
    fun before() {
        testee = WebCompatContentScopeConfigPlugin(mockWebCompatRepository)
    }

    @Test
    fun whenGetConfigThenReturnCorrectlyFormattedJson() {
        whenever(mockWebCompatRepository.getWebCompatEntity()).thenReturn(WebCompatEntity(json = config))
        assertEquals("\"webCompat\":$config", testee.config())
    }

    @Test
    fun whenGetPreferencesThenReturnNull() {
        assertNull(testee.preferences())
    }

    companion object {
        const val config = "{\"key\":\"value\"}"
    }
}
