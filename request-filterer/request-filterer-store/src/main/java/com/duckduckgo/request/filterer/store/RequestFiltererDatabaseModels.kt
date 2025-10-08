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
package com.duckduckgo.request.filterer.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duckduckgo.feature.toggles.api.FeatureException

@Entity(tableName = "settings_entity")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val windowInMs: Int,
)

@Entity(tableName = "request_filterer_exceptions")
data class RequestFiltererExceptionEntity(
    @PrimaryKey val domain: String,
    val reason: String,
)

fun RequestFiltererExceptionEntity.toFeatureException(): FeatureException {
    return FeatureException(domain = this.domain, reason = this.reason)
}
