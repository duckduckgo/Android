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

/** Any feature toggles implemented in any module should implement [FeatureToggle] */
@Deprecated(
    message = "Use the new feature flag framework, https://app.asana.com/0/1202552961248957/1203898052213029/f",
    level = DeprecationLevel.WARNING,
)
interface FeatureToggle {
    /**
     * This method takes a [featureName] and optionally a default value.
     * @return `true` if the feature is enabled, `false` if is not
     * @throws [IllegalArgumentException] if the feature is not implemented
     */
    fun isFeatureEnabled(
        featureName: String,
        defaultValue: Boolean = true,
    ): Boolean
}
