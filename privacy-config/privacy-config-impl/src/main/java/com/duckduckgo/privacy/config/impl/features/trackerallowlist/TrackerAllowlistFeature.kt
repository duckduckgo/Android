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

package com.duckduckgo.privacy.config.impl.features.trackerallowlist

import com.duckduckgo.privacy.config.store.AllowlistRuleEntity

data class TrackerAllowlistFeature(
    val state: String,
    val minSupportedVersion: Int?,
    val settings: TrackerAllowSettings
)

data class TrackerAllowSettings(val allowlistedTrackers: Map<String, AllowListedTrackers>)

data class AllowListedTrackers(val rules: List<AllowlistRuleEntity>)
