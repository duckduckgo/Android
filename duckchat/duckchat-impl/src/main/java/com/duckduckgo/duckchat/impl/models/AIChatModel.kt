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

package com.duckduckgo.duckchat.impl.models

import com.squareup.moshi.Json

data class AIChatModelsResponse(
    val models: List<RemoteAIChatModel>,
)

data class RemoteAIChatModel(
    val id: String,
    val name: String,
    @field:Json(name = "displayName") val displayName: String? = null,
    @field:Json(name = "modelShortName") val shortName: String? = null,
    @field:Json(name = "accessTier") val accessTier: List<String>? = null,
    @field:Json(name = "entityHasAccess") val entityHasAccess: Boolean = false,
)

data class AIChatModel(
    val id: String,
    val name: String,
    val displayName: String,
    val shortName: String,
    val accessTier: List<String>,
    val isAccessible: Boolean,
)

enum class UserTier(val rawValue: String) {
    FREE("free"),
    PLUS("plus"),
    PRO("pro"),
}
