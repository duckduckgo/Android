/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.statistics.wideevents.api

import com.squareup.moshi.Json

data class WideEventRequest(
    @field:Json(name = "global") val global: GlobalSection,
    @field:Json(name = "app") val app: AppSection,
    @field:Json(name = "feature") val feature: FeatureSection,
    @field:Json(name = "context") val context: ContextSection?,
)

data class GlobalSection(
    @field:Json(name = "platform") val platform: String,
    @field:Json(name = "type") val type: String,
    @field:Json(name = "sample_rate") val sampleRate: Int,
)

data class AppSection(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "version") val version: String,
    @field:Json(name = "form_factor") val formFactor: String,
    @field:Json(name = "dev_mode") val devMode: Boolean,
)

data class FeatureSection(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "status") val status: String,
    @field:Json(name = "data") val data: FeatureData?,
)

data class FeatureData(
    @field:Json(name = "ext") val ext: Map<String, String>?,
)

data class ContextSection(
    @field:Json(name = "name") val name: String,
)
