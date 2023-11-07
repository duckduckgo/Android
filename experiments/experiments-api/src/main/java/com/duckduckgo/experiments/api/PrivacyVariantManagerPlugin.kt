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

package com.duckduckgo.experiments.api

/**
 * Implement this interface and contribute it as a multibinding to get called upon downloading remote variant manager config
 *
 * Usage:
 *
 * ```kotlin
 * @ContributesMultibinding(AppScope::class)
 * class MuFeaturePlugin @Inject constructor(...) : PrivacyVariantManagerPlugin {
 *
 * }
 * ```
 */
interface PrivacyVariantManagerPlugin {

    /**
     * @return `true` when the variants are stored, otherwise `false`
     */
    fun store(jsonString: String): Boolean
}
