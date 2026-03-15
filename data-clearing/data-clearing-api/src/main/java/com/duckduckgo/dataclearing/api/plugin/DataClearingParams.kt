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

package com.duckduckgo.dataclearing.api.plugin

/**
 * Describes what data should be cleaned up.
 *
 * The [types] set contains one or more [DataType] entries describing
 * the kinds of data to clear. Plugins inspect these to decide what action to take.
 */
data class DataClearingParams(
    val types: Set<DataType>,
)
