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

package com.duckduckgo.autofill.api.feature

interface AutofillFeatureToggle {
    /**
     * This method takes a [featureName] for a corresponding [AutofillSubfeatureName] and optionally a default value.
     * @return `true` if the feature is enabled, `false` if is not, or `null` if we have no data for the given feature.
     */
    fun isFeatureEnabled(
        featureName: String,
        defaultValue: Boolean = true,
    ): Boolean?
}
