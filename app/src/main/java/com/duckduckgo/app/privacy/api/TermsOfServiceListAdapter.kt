/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.api

import com.duckduckgo.app.privacy.model.TermsOfService
import com.squareup.moshi.FromJson

class TermsOfServiceListAdapter {

    @FromJson
    fun fromJson(json: Map<String, TermsOfServiceJson>): List<TermsOfService> {
        val tos = ArrayList<TermsOfService>()
        for (entry in json) {
            val classification = entry.value.classification as? String
            tos.add(
                TermsOfService(
                    entry.key,
                    entry.value.score,
                    classification,
                    entry.value.match.good,
                    entry.value.match.bad))
        }
        return tos
    }
}
