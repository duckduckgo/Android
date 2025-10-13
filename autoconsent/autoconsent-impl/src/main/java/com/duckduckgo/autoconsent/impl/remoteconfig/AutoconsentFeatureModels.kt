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

package com.duckduckgo.autoconsent.impl.remoteconfig

import com.squareup.moshi.Json

class AutoconsentFeatureModels {

    data class AutoconsentSettings(
        @field:Json(name = "disabledCMPs")
        val disabledCMPs: List<String>,
        @field:Json(name = "compactRuleList")
        val compactRuleList: CompactRules,
    )

    data class CompactRules(
        val v: Int,
        val r: List<List<Any>>,
        val s: List<String>,
        val index: CompactRuleIndex?,
    )

    data class CompactRuleIndex(
        val genericRuleRange: List<Int>,
        val frameRuleRange: List<Int>,
        val specificRuleRange: List<Int>,
        val genericStringEnd: Int,
        val frameStringEnd: Int,
    )
}
