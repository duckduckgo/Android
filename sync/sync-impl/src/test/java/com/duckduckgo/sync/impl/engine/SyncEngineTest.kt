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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.impl.SyncRepository
import org.junit.Before
import org.mockito.kotlin.mock

internal class SyncEngineTest {

    private val syncRepository: SyncRepository = mock()
    private val syncApiClient: SyncApiClient = mock()
    private val syncScheduler: SyncScheduler = mock()
    private val syncStateRepository: SyncStateRepository = mock()
    private val plugins: PluginPoint<SyncablePlugin> = mock()
    private lateinit var syncEngine: RealSyncEngine

    @Before
    fun before() {
        syncEngine = RealSyncEngine(syncRepository, syncApiClient, syncScheduler, syncStateRepository, plugins)
    }



}
