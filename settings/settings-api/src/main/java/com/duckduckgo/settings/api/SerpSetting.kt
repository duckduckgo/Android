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

package com.duckduckgo.settings.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A setting the DuckDuckGo SERP exchanges with the app: [serpKey] names it inside the `serpSettings` blob
 * and [serpCode] is the SERP's fixed encoding of this option.
 */
interface SerpSetting {
    val serpKey: String
    val serpCode: String
}

suspend fun SerpSettingsDataProvider.setSetting(setting: SerpSetting) = setSetting(setting.serpKey, setting.serpCode)

/**
 * Observes [T], falling back to [default] both while the SERP has not provided a value and when it provides
 * one this version of the app doesn't recognize.
 */
inline fun <reified T> SerpSettingsDataProvider.observeSetting(default: T): Flow<T>
    where T : Enum<T>, T : SerpSetting =
    observeSetting(default.serpKey).map { raw -> enumValues<T>().firstOrNull { it.serpCode == raw } ?: default }
