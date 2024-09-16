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

package com.duckduckgo.voice.impl.remoteconfig

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VoiceSearchSetting(
    @field:Json(name = "excludedManufacturers")
    val excludedManufacturers: List<Manufacturer>,
    @field:Json(name = "excludedLocales")
    val excludedLocales: List<Locale> = emptyList(),
    @field:Json(name = "minVersion")
    val minVersion: Int,
)

@JsonClass(generateAdapter = true)
data class Manufacturer(
    @field:Json(name = "name")
    val name: String,
)

@JsonClass(generateAdapter = true)
data class Locale(
    @field:Json(name = "name")
    val name: String,
)
