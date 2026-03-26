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

package com.duckduckgo.duckplayer.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayerFeatureName
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckPlayerContentScopeConfigPlugin @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val appBuildConfig: AppBuildConfig,
    private val duckPlayer: DuckPlayer,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val featureName = DuckPlayerFeatureName.DuckPlayer.value

        val config = duckPlayerFeatureRepository.getDuckPlayerRemoteConfigJson().let { jsonString ->
            if (appBuildConfig.isInternalBuild() && runBlocking { duckPlayer.getDuckPlayerState() == ENABLED }) {
                runCatching {
                    JSONObject(jsonString).takeIf { it.getString("state") == "internal" }?.apply {
                        put("state", "enabled")
                    }?.toString() ?: jsonString
                }.getOrDefault(jsonString)
            } else {
                jsonString
            }
        }

        return "\"$featureName\":$config"
    }

    override fun preferences(): String? {
        return null
    }
}
