/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.migrations

import com.duckduckgo.app.global.migrations.MigrationLifecycleObserver.Companion.CURRENT_VERSION
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.migrations.MigrationPlugin
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MigrationLifecycleObserverTest {

    private val mockMigrationStore: MigrationStore = mock()
    private val pluginPoint = FakeMigrationPluginPoint()

    private lateinit var testee: MigrationLifecycleObserver

    @Before
    fun before() {
        testee = MigrationLifecycleObserver(pluginPoint, mockMigrationStore)
    }

    @Test
    fun whenMigrateIfStoredVersionIsLowerThanCurrentThenRunMigrations() {
        whenever(mockMigrationStore.version).thenReturn(CURRENT_VERSION - 1)

        testee.migrate()

        val plugin = pluginPoint.getPlugins().first() as FakeMigrationPlugin
        assertEquals(1, plugin.count)
    }

    @Test
    fun whenMigrateIfStoredVersionIsLowerThanCurrentThenStoreCurrentVersion() {
        whenever(mockMigrationStore.version).thenReturn(CURRENT_VERSION - 1)

        testee.migrate()

        verify(mockMigrationStore).version = CURRENT_VERSION
    }

    @Test
    fun whenMigrateIfStoredVersionIsHigherThanCurrentThenDoNotRunMigrations() {
        whenever(mockMigrationStore.version).thenReturn(CURRENT_VERSION + 1)

        testee.migrate()

        val plugin = pluginPoint.getPlugins().first() as FakeMigrationPlugin
        assertEquals(0, plugin.count)
    }

    @Test
    fun whenMigrateIfStoredVersionIsEqualsThanCurrentThenDoNotRunMigrations() {
        whenever(mockMigrationStore.version).thenReturn(CURRENT_VERSION)

        testee.migrate()

        val plugin = pluginPoint.getPlugins().first() as FakeMigrationPlugin
        assertEquals(0, plugin.count)
    }

    internal class FakeMigrationPluginPoint : PluginPoint<MigrationPlugin> {
        val plugin = FakeMigrationPlugin()
        override fun getPlugins(): Collection<MigrationPlugin> {
            return listOf(plugin)
        }
    }

    internal class FakeMigrationPlugin(override val version: Int = CURRENT_VERSION) : MigrationPlugin {
        var count = 0

        override fun run() {
            count++
        }
    }
}
