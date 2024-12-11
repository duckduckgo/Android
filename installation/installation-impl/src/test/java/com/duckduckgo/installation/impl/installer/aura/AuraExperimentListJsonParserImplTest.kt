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

package com.duckduckgo.installation.impl.installer.com.duckduckgo.installation.impl.installer.aura

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.installation.impl.installer.aura.AuraExperimentListJsonParserImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuraExperimentListJsonParserImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = AuraExperimentListJsonParserImpl(coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenGibberishInputThenReturnsReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("invalid json")
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListIsMissingFieldThenReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("{}")
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListIsEmptyThenReturnsEmptyPackages() = runTest {
        val result = testee.parseJson("auraExperiment_emptyList".loadJsonFile())
        assertTrue(result.list.isEmpty())
    }

    @Test
    fun whenInstallerListHasSingleEntryThenReturnsSinglePackage() = runTest {
        val result = testee.parseJson("auraExperiment_singleEntryList".loadJsonFile())
        assertEquals(1, result.list.size)
        assertEquals("a.b.c", result.list[0])
    }

    @Test
    fun whenInstallerListHasMultipleEntriesThenReturnsMultiplePackages() = runTest {
        val result = testee.parseJson("auraExperiment_multipleEntryList".loadJsonFile())
        assertEquals(3, result.list.size)
        assertEquals("a.b.c", result.list[0])
        assertEquals("d.e.f", result.list[1])
        assertEquals("g.h.i", result.list[2])
    }

    private fun String.loadJsonFile(): String {
        return FileUtilities.loadText(
            AuraExperimentListJsonParserImplTest::class.java.classLoader!!,
            "json/$this.json",
        )
    }
}
