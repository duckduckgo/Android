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

package com.duckduckgo.app.statistics.wideevents

object WideEventMeta {
    const val DEFAULT_VERSION = "1.0.0"

    /** `meta.type` is kebab-case across all platforms, hence the separator normalization. */
    fun typeFor(eventName: String): String = TYPE_PREFIX + eventName.replace('_', '-')

    private const val TYPE_PREFIX = "android-"
}
