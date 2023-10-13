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

package com.duckduckgo.adclick.impl

import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException

data class AdClickAttributionFeature(
    val state: String,
    val minSupportedVersion: Int?,
    val settings: AdClickAttributionSettings,
    val exceptions: List<FeatureException>,
)

data class AdClickAttributionSettings(
    val linkFormats: List<AdClickAttributionLinkFormat>,
    val allowlist: List<AdClickAttributionAllowlist>,
    val navigationExpiration: Long,
    val totalExpiration: Long,
    val heuristicDetection: String?,
    val domainDetection: String?,
)

data class AdClickAttributionLinkFormat(
    val url: String,
    val adDomainParameterName: String?,
)

data class AdClickAttributionAllowlist(
    val blocklistEntry: String?,
    val host: String?,
)
