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

package com.duckduckgo.settings.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

enum class TestSerpSetting(override val serpCode: String) : SerpSetting {
    FIRST("1"),
    SECOND("2"),
    ;

    override val serpKey = "test"
}

class SerpSettingTest {

    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()

    @Test
    fun `when setting written then its key and code are stored`() = runTest {
        serpSettingsDataProvider.setSetting(TestSerpSetting.SECOND)

        verify(serpSettingsDataProvider).setSetting("test", "2")
    }

    @Test
    fun `when stored code is known then it decodes to its option`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("test")).thenReturn(flowOf("2"))

        assertEquals(TestSerpSetting.SECOND, serpSettingsDataProvider.observeSetting(TestSerpSetting.FIRST).first())
    }

    @Test
    fun `when nothing stored then default is emitted`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("test")).thenReturn(flowOf(null))

        assertEquals(TestSerpSetting.FIRST, serpSettingsDataProvider.observeSetting(TestSerpSetting.FIRST).first())
    }

    @Test
    fun `when stored code is unrecognized then default is emitted`() = runTest {
        whenever(serpSettingsDataProvider.observeSetting("test")).thenReturn(flowOf("99"))

        assertEquals(TestSerpSetting.FIRST, serpSettingsDataProvider.observeSetting(TestSerpSetting.FIRST).first())
    }
}
