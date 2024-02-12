/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.api

/**
 * Implement this interface and contribute it as a multibinding to get called upon downloading remote privacy config
 *
 * Usage:
 *
 * ```kotlin
 * @ContributesMultibinding(AppScope::class)
 * class MuFeaturePlugin @Inject constructor(...) : PrivacyFeaturePlugin {
 *
 * }
 * ```
 */
interface PrivacyFeaturePlugin {
    /**
     * @return `true` when the feature was stored, otherwise `false`
     */
    fun store(
        featureName: String,
        jsonString: String,
    ): Boolean

    /**
     * @return the [featureName] of this feature
     */
    val featureName: String

    /**
     * @return the hash that represents the feature.
     * The has should change every time the feature changes, eg. new sub-feature is added/remove
     * Default value is set to [null] for backwards compatibility
     */
    fun hash(): String? = null
}
