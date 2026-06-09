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

package com.duckduckgo.testseeder.internal

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class RealTestScenarioSeederTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun whenIsMaestroIsAbsentThenNoPluginIsCalled() = runTest {
        val plugin = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val seeder = newSeeder(plugin)

        seeder.seedIfNeeded(mapOf(TestSeederKey.OMNIBAR_POSITION.key to "bottom"))

        assertEquals(emptyList<Pair<String, String>>(), plugin.calls)
    }

    @Test
    fun whenIsMaestroIsFalseThenNoPluginIsCalled() = runTest {
        val plugin = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val seeder = newSeeder(plugin)

        seeder.seedIfNeeded(
            mapOf(
                TestSeederKey.IS_MAESTRO.key to "false",
                TestSeederKey.OMNIBAR_POSITION.key to "bottom",
            ),
        )

        assertEquals(emptyList<Pair<String, String>>(), plugin.calls)
    }

    @Test
    fun whenIsMaestroIsTrueAndNoOtherKeysThenNoPluginIsCalled() = runTest {
        val plugin = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val seeder = newSeeder(plugin)

        seeder.seedIfNeeded(mapOf(TestSeederKey.IS_MAESTRO.key to "true"))

        assertEquals(emptyList<Pair<String, String>>(), plugin.calls)
    }

    @Test
    fun whenKeyHasPluginThenPluginIsAppliedWithKeyAndValue() = runTest {
        val plugin = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val seeder = newSeeder(plugin)

        seeder.seedIfNeeded(
            mapOf(
                TestSeederKey.IS_MAESTRO.key to "true",
                TestSeederKey.OMNIBAR_POSITION.key to "bottom",
            ),
        )

        assertEquals(listOf(TestSeederKey.OMNIBAR_POSITION.key to "bottom"), plugin.calls)
    }

    @Test
    fun whenMultipleKeysAreSetThenEachPluginIsAppliedInSortedKeyOrder() = runTest {
        val omnibar = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val nativeInput = RecordingPlugin(TestSeederKey.NATIVE_INPUT_TOGGLE.key)
        val favorites = RecordingPlugin(TestSeederKey.ADD_FAVORITES.key)
        val recordedOrder = mutableListOf<String>()
        omnibar.onApply = { key, _ -> recordedOrder += key }
        nativeInput.onApply = { key, _ -> recordedOrder += key }
        favorites.onApply = { key, _ -> recordedOrder += key }
        val seeder = newSeeder(omnibar, nativeInput, favorites)

        seeder.seedIfNeeded(
            mapOf(
                TestSeederKey.IS_MAESTRO.key to "true",
                TestSeederKey.OMNIBAR_POSITION.key to "top",
                TestSeederKey.NATIVE_INPUT_TOGGLE.key to "true",
                TestSeederKey.ADD_FAVORITES.key to "3",
            ),
        )

        val expectedSortedOrder = listOf(
            TestSeederKey.ADD_FAVORITES.key,
            TestSeederKey.NATIVE_INPUT_TOGGLE.key,
            TestSeederKey.OMNIBAR_POSITION.key,
        )
        assertEquals(expectedSortedOrder, recordedOrder)
    }

    @Test
    fun whenAnUnknownKeyIsPassedAtRuntimeThenSeederThrows() = runTest {
        val plugin = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val seeder = newSeeder(plugin)

        val thrown = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                seeder.seedIfNeeded(
                    mapOf(
                        TestSeederKey.IS_MAESTRO.key to "true",
                        "definitelyNotAKey" to "x",
                    ),
                )
            }
        }
        assertEquals(true, thrown.message!!.contains("definitelyNotAKey"))
    }

    @Test
    fun whenPluginClaimsAnUndeclaredKeyThenConstructionThrows() {
        val rogue = RecordingPlugin("notInTheRegistry")

        assertThrows(IllegalStateException::class.java) { newSeeder(rogue) }
    }

    @Test
    fun whenTwoPluginsClaimTheSameKeyThenConstructionThrows() {
        val a = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)
        val b = RecordingPlugin(TestSeederKey.OMNIBAR_POSITION.key)

        assertThrows(IllegalStateException::class.java) { newSeeder(a, b) }
    }

    private fun newSeeder(vararg plugins: TestSeederPlugin): RealTestScenarioSeeder =
        RealTestScenarioSeeder(plugins.toSet(), coroutineRule.testDispatcherProvider)

    private class RecordingPlugin(
        vararg keys: String,
    ) : TestSeederPlugin {
        override val handledKeys: Set<String> = keys.toSet()
        val calls = mutableListOf<Pair<String, String>>()
        var onApply: ((String, String) -> Unit)? = null

        override suspend fun apply(key: String, value: String) {
            calls += key to value
            onApply?.invoke(key, value)
        }
    }
}
