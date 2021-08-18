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

package com.duckduckgo.privacy.config.store

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_blocking_exceptions")
data class ContentBlockingException(
    @PrimaryKey val domain: String,
    val reason: String
)

@Entity(tableName = "toggles")
data class PrivacyFeatureToggles(
    @PrimaryKey val featureName: String,
    val enabled: Boolean
)

@Entity(tableName = "privacy_config")
data class PrivacyConfig(
    @PrimaryKey val id: Int = 1,
    val version: Long,
    val readme: String
)
