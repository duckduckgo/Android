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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Content Scope config plugin for the browserUiLock feature.
 *
 * This plugin provides the feature configuration to C-S-S when the feature
 * is enabled in the remote config. The configuration is minimal as most logic
 * is handled by the JavaScript feature detection.
 */
@ContributesMultibinding(AppScope::class)
class BrowserUiLockContentScopeConfigPlugin @Inject constructor(
    private val browserUiLockRepository: BrowserUiLockRepository,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val config = browserUiLockRepository.getJsonData()
        return "\"$FEATURE_NAME\":$config"
    }

    override fun preferences(): String? = null

    companion object {
        const val FEATURE_NAME = "browserUiLock"
    }
}
