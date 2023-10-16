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

package com.duckduckgo.privacy.config.impl.features.useragent

import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException

data class UserAgentFeature(
    val state: String,
    val minSupportedVersion: Int?,
    val exceptions: List<FeatureException>,
    val settings: UserAgentSettings,
)

data class UserAgentSettings(
    val defaultPolicy: String?,
    val ddgDefaultSites: List<FeatureException>,
    val ddgFixedSites: List<FeatureException>,
    val closestUserAgent: ClosestUserAgent?,
    val ddgFixedUserAgent: DdgFixedUserAgent?,
    val omitApplicationSites: List<FeatureException>,
    val omitVersionSites: List<FeatureException>,
)

data class ClosestUserAgent(
    val versions: List<String>,
    val state: String,
)

data class DdgFixedUserAgent(
    val versions: List<String>,
    val state: String,
)
