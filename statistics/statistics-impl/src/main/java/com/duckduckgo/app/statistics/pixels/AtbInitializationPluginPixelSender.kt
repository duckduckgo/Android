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

package com.duckduckgo.app.statistics.pixels

import com.duckduckgo.app.statistics.pixels.StatisticsPixelName.ATB_PRE_INITIALIZER_PLUGIN_TIMEOUT
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

interface AtbInitializationPluginPixelSender {
    fun pluginTimedOut(pluginName: String)
}

@ContributesBinding(AppScope::class)
class RealAtbInitializationPluginPixelSender @Inject constructor(
    private val pixel: Pixel,
) : AtbInitializationPluginPixelSender {

    override fun pluginTimedOut(pluginName: String) {
        logcat(LogPriority.ERROR) { "AtbInitializer: pre-init plugin timed out [$pluginName]" }

        val params = mapOf(
            "plugin" to pluginName,
        )
        pixel.fire(ATB_PRE_INITIALIZER_PLUGIN_TIMEOUT, parameters = params, emptyMap())
    }
}

@ContributesMultibinding(AppScope::class)
class AtbInitializerPluginPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            ATB_PRE_INITIALIZER_PLUGIN_TIMEOUT.pixelName to PixelParameter.removeAtb(),
        )
    }
}
