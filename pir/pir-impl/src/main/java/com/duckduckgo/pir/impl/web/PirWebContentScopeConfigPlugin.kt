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

package com.duckduckgo.pir.impl.web

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PirWebContentScopeConfigPlugin @Inject constructor(
    // private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val appBuildConfig: AppBuildConfig,
    // private val duckPlayer: DuckPlayer,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val featureName = "dbp"

        val config = "{\"state\":\"enabled\",\"features\":{\"waitlist\":{\"state\":\"disabled\"},\"waitlistBetaActive\":{\"state\":\"disabled\"},\"freemium\":{\"state\":\"disabled\"}},\"exceptions\":[],\"hash\":\"113933668f41d71c56316e5debf1fb5b\"}"

        return "\"$featureName\":$config,\"dbpui\":$config,\"dbpuiCommunication\":$config"
    }

    override fun preferences(): String? {
        return null
    }
}
