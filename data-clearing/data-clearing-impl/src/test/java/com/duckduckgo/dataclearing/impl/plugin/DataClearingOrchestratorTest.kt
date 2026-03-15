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
import com.duckduckgo.dataclearing.api.plugin.ClearResult
import com.duckduckgo.dataclearing.api.plugin.DataClearingParams
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.dataclearing.api.plugin.DataType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataClearingOrchestratorTest {

    @Test
    fun whenClearDataCalledThenAllPluginsReceiveParams() = runTest {
        val receivedParams = mutableListOf<DataClearingParams>()
        val plugin1 = recordingPlugin(receivedParams)
        val plugin2 = recordingPlugin(receivedParams)
        val orchestrator = createOrchestrator(plugin1, plugin2)

        val params = DataClearingParams(setOf(DataType.Tabs.All))
        orchestrator.clearData(params)

        assertEquals(2, receivedParams.size)
        assertTrue(receivedParams.all { it == params })
    }

    @Test
    fun whenPluginFailsThenOtherPluginsStillExecute() = runTest {
        val receivedParams = mutableListOf<DataClearingParams>()
        val failingPlugin = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                throw RuntimeException("boom")
            }
        }
        val successPlugin = recordingPlugin(receivedParams)
        val orchestrator = createOrchestrator(failingPlugin, successPlugin)

        orchestrator.clearData(DataClearingParams(setOf(DataType.Tabs.All)))

        assertEquals(1, receivedParams.size)
    }

    @Test
    fun whenPluginReturnsChainThenAdditionalTypesAreProcessed() = runTest {
        val allReceivedTypes = mutableListOf<Set<DataType>>()
        val chainingPlugin = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                allReceivedTypes.add(params.types)
                return if (params.types.any { it is DataType.Tabs.Single }) {
                    ClearResult.Chain(setOf(DataType.BrowserData.ForDomains(setOf("example.com"))))
                } else {
                    ClearResult.Success
                }
            }
        }
        val orchestrator = createOrchestrator(chainingPlugin)

        orchestrator.clearData(DataClearingParams(setOf(DataType.Tabs.Single("tab1"))))

        assertEquals(2, allReceivedTypes.size)
        assertTrue(allReceivedTypes[0].any { it is DataType.Tabs.Single })
        assertTrue(allReceivedTypes[1].any { it is DataType.BrowserData.ForDomains })
    }

    @Test
    fun whenChainProducesSameTypeThenItIsNotReprocessed() = runTest {
        var callCount = 0
        val loopingPlugin = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                callCount++
                // Always try to chain back the same type
                return ClearResult.Chain(setOf(DataType.Tabs.All))
            }
        }
        val orchestrator = createOrchestrator(loopingPlugin)

        orchestrator.clearData(DataClearingParams(setOf(DataType.Tabs.All)))

        // Round 1: processes Tabs.All, chains Tabs.All
        // Round 2: Tabs.All already processed, stops
        assertEquals(1, callCount)
    }

    @Test
    fun whenMultiplePluginsChainDifferentParamsForSameTypeThenBothAreProcessed() = runTest {
        val allReceivedTypes = mutableListOf<Set<DataType>>()
        val plugin1 = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                allReceivedTypes.add(params.types)
                return if (params.types.any { it is DataType.Tabs.Single }) {
                    ClearResult.Chain(setOf(DataType.BrowserData.ForDomains(setOf("a.com"))))
                } else {
                    ClearResult.Success
                }
            }
        }
        val plugin2 = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                allReceivedTypes.add(params.types)
                return if (params.types.any { it is DataType.Tabs.Single }) {
                    ClearResult.Chain(setOf(DataType.BrowserData.ForDomains(setOf("b.com"))))
                } else {
                    ClearResult.Success
                }
            }
        }
        val orchestrator = createOrchestrator(plugin1, plugin2)

        orchestrator.clearData(DataClearingParams(setOf(DataType.Tabs.Single("tab1"))))

        // Round 1: both plugins get Tabs.Single, chain ForDomains(a.com) and ForDomains(b.com)
        // Round 2: both plugins get {ForDomains(a.com), ForDomains(b.com)}
        val round2Types = allReceivedTypes[2]
        assertTrue(round2Types.contains(DataType.BrowserData.ForDomains(setOf("a.com"))))
        assertTrue(round2Types.contains(DataType.BrowserData.ForDomains(setOf("b.com"))))
    }

    @Test
    fun whenMultiplePluginsChainSameTypeAndParamsThenOnlyProcessedOnce() = runTest {
        var round2Count = 0
        val plugin = object : DataClearingPlugin {
            override suspend fun onClearData(params: DataClearingParams): ClearResult {
                if (params.types.any { it is DataType.BrowserData.ForDomains }) {
                    round2Count++
                }
                return if (params.types.any { it is DataType.Tabs.Single }) {
                    ClearResult.Chain(setOf(DataType.BrowserData.ForDomains(setOf("same.com"))))
                } else {
                    ClearResult.Success
                }
            }
        }
        // Two plugins both chain the exact same ForDomains
        val orchestrator = createOrchestrator(plugin, plugin)

        orchestrator.clearData(DataClearingParams(setOf(DataType.Tabs.Single("tab1"))))

        // ForDomains(same.com) deduped in set, processed once in round 2, both plugins called
        assertEquals(2, round2Count)
    }

    private fun recordingPlugin(list: MutableList<DataClearingParams>) = object : DataClearingPlugin {
        override suspend fun onClearData(params: DataClearingParams): ClearResult {
            list.add(params)
            return ClearResult.Success
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
