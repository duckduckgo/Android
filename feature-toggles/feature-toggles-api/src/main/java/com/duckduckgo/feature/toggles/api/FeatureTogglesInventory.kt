/*
 * Copyright (c) 2024 DuckDuckGo
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

interface FeatureTogglesInventory {
    /**
     * @return returns a list of ALL feature flags (aka [Toggle]s) currently used in the app
     */
    suspend fun getAll(): List<Toggle>

    /**
     * @return returns the list of all sub-features for a given top level feature
     */
    suspend fun getAllTogglesForParent(name: String): List<Toggle> = emptyList()

    /**
     * @return returns ALL toggles that have an assigned cohort AND "state": "enabled"
     */
    suspend fun getAllActiveExperimentToggles(): List<Toggle> = emptyList()
}
