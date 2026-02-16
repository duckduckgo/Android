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
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

private const val FEATURE_NAME = "browserUiLock"
private const val DEFAULT_CONFIG = """{"state":"enabled","exceptions":[]}"""

/**
 * Handles browser UI lock feature configuration.
 * - Receives config from remote via PrivacyFeaturePlugin
 * - Provides config to C-S-S via ContentScopeConfigPlugin
 */
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyFeaturePlugin::class)
@ContributesMultibinding(AppScope::class, boundType = ContentScopeConfigPlugin::class)
class BrowserUiLockFeature @Inject constructor() : PrivacyFeaturePlugin, ContentScopeConfigPlugin {

    @Volatile
    private var configJson: String = DEFAULT_CONFIG

    // PrivacyFeaturePlugin - receives config from remote
    override fun store(featureName: String, jsonString: String): Boolean {
        if (featureName == FEATURE_NAME) {
            configJson = jsonString
            return true
        }
        return false
    }

    override val featureName: String = FEATURE_NAME

    // ContentScopeConfigPlugin - provides config to C-S-S
    override fun config(): String = "\"$FEATURE_NAME\":$configJson"

    override fun preferences(): String? = null
}
