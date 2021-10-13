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

package com.duckduckgo.feature.toggles.api

/**
 * Features that can be enabled/disabled should implement this plugin.
 * The associated plugin point will call the plugins when the [FeatureToggle] API
 * is used
 */
interface FeatureTogglesPlugin {
    /**
     * This method will return whether the plugin knows about the [featureName] in which case will return
     * whether it is enabled or disabled
     * @return `true` if the feature is enabled. `false` when disabled. `null` if the plugin does not know the featureName.
     * [defaultValue] if the plugin knows featureName but is not set
     */
    suspend fun isEnabled(featureName: FeatureName, defaultValue: Boolean): Boolean?
}
