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

package com.duckduckgo.mobile.android.vpn.remote_config

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter

@Entity(tableName = "vpn_config_toggles")
data class VpnConfigToggle(
    @PrimaryKey(autoGenerate = false) val name: String,
    val enabled: Boolean,
    val isManualOverride: Boolean = false,
    val localtime: String = DatabaseDateFormatter.timestamp(),
)
