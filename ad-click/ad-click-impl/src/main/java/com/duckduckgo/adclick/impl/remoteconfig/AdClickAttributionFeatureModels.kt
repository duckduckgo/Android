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

package com.duckduckgo.adclick.impl.remoteconfig

import com.squareup.moshi.Json

data class AdClickAttributionFeatureModel(
    @field:Json(name = "linkFormats")
    val linkFormats: List<AdClickAttributionLinkFormat>,
    @field:Json(name = "allowlist")
    val allowlist: List<AdClickAttributionAllowlist>,
    @field:Json(name = "navigationExpiration")
    val navigationExpiration: Long,
    @field:Json(name = "totalExpiration")
    val totalExpiration: Long,
    @field:Json(name = "heuristicDetection")
    val heuristicDetection: String?,
    @field:Json(name = "domainDetection")
    val domainDetection: String?,
)

data class AdClickAttributionLinkFormat(
    @field:Json(name = "url")
    val url: String,
    @field:Json(name = "adDomainParameterName")
    val adDomainParameterName: String?,
)

data class AdClickAttributionAllowlist(
    @field:Json(name = "blocklistEntry")
    val blocklistEntry: String?,
    @field:Json(name = "host")
    val host: String?,
)
