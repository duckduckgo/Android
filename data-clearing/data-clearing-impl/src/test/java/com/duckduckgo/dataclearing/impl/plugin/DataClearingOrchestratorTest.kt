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

package com.duckduckgo.dataclearing.impl.plugin

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataClearingOrchestratorTest {

    @Test
    fun whenClearDataCalledThenAllPluginsReceiveTypes() = runTest {
        val receivedTypes = mutableListOf<Set<ClearableData>>()
        val plugin1 = recordingPlugin(receivedTypes)
        val plugin2 = recordingPlugin(receivedTypes)
        val orchestrator = createOrchestrator(plugin1, plugin2)

        val types = setOf<ClearableData>(ClearableData.Tabs.All)
        orchestrator.clearData(types)

        assertEquals(2, receivedTypes.size)
        assertTrue(receivedTypes.all { it == types })
    }

    @Test
    fun whenPluginFailsThenOtherPluginsStillExecute() = runTest {
        val receivedTypes = mutableListOf<Set<ClearableData>>()
        val failingPlugin = object : DataClearingPlugin {
            override suspend fun onClearData(types: Set<ClearableData>) {
                throw RuntimeException("boom")
            }
        }
        val successPlugin = recordingPlugin(receivedTypes)
        val orchestrator = createOrchestrator(failingPlugin, successPlugin)

        orchestrator.clearData(setOf(ClearableData.Tabs.All))

        assertEquals(1, receivedTypes.size)
    }

    @Test
    fun whenMultipleTypesProvidedThenAllPluginsReceiveFullSet() = runTest {
        val receivedTypes = mutableListOf<Set<ClearableData>>()
        val plugin = recordingPlugin(receivedTypes)
        val orchestrator = createOrchestrator(plugin)

        val types = setOf<ClearableData>(ClearableData.Tabs.Single("tab1"), ClearableData.DuckChats.Single("url"))
        orchestrator.clearData(types)

        assertEquals(1, receivedTypes.size)
        assertEquals(types, receivedTypes[0])
    }

    private fun recordingPlugin(list: MutableList<Set<ClearableData>>) = object : DataClearingPlugin {
        override suspend fun onClearData(types: Set<ClearableData>) {
            list.add(types)
        }
    }

    private fun createOrchestrator(vararg plugins: DataClearingPlugin): DataClearingOrchestrator {
        return DataClearingOrchestrator(
            plugins = object : PluginPoint<DataClearingPlugin> {
                override fun getPlugins(): Collection<DataClearingPlugin> = plugins.toList()
            },
        )
    }
}
