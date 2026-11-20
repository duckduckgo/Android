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

package com.duckduckgo.agesignals.impl.dev

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AgeSignalsInternalFeaturePluginModuleTest {

    private val plugin = AgeSignalsInternalFeaturePlugin(mock())

    @Test
    fun whenInternalBuildThenTheDevScreenPluginIsContributed() {
        assertEquals(setOf(plugin), pluginsFor(BuildFlavor.INTERNAL))
    }

    @Test
    fun whenNonInternalBuildThenNoPluginIsContributed() {
        BuildFlavor.entries.filter { it != BuildFlavor.INTERNAL }.forEach { flavor ->
            assertEquals("$flavor", emptySet<Any>(), pluginsFor(flavor))
        }
    }

    private fun pluginsFor(flavor: BuildFlavor) = AgeSignalsInternalFeaturePluginModule.provideInternalFeaturePlugins(
        appBuildConfig = mock<AppBuildConfig> { on { this.flavor } doReturn flavor },
        plugin = plugin,
    )
}
