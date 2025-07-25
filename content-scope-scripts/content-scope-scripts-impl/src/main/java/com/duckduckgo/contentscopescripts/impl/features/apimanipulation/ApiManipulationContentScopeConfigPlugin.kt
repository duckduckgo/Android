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

package com.duckduckgo.contentscopescripts.impl.features.apimanipulation

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

const val API_MANIPULATION_FEATURE_NAME = "apiManipulation"

@ContributesMultibinding(AppScope::class)
class ApiManipulationContentScopeConfigPlugin @Inject constructor(
    private val apiManipulationRepository: ApiManipulationRepository,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val config = runBlocking { apiManipulationRepository.getJsonData() } ?: "{}"
        return "\"$API_MANIPULATION_FEATURE_NAME\":$config"
    }

    override fun preferences(): String? {
        return null
    }
}
