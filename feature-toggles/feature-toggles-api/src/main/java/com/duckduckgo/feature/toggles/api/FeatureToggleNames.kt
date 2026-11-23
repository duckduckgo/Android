/*
 * Copyright (c) 2026 DuckDuckGo
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
 * The names of a remote feature and its sub-features, known at compile time.
 *
 * Every `@ContributesRemoteFeature` contributes one into a set, so consumers can list all feature flags
 * without creating the features, unlike [FeatureTogglesInventory].
 *
 * @param featureName the name of the top level feature
 * @param subFeatureNames the names of its sub-features, without the parent prefix
 */
data class FeatureToggleNames(
    val featureName: String,
    val subFeatureNames: List<String>,
)
