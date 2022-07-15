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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.global.initialization.DataLoadable

interface PrivacyPractices : DataLoadable {

    enum class Summary {
        POOR,
        GOOD,
        MIXED,
        UNKNOWN
    }

    data class Practices(
        val score: Int,
        val summary: Summary,
        val goodReasons: List<String>,
        val badReasons: List<String>
    )

    fun privacyPracticesFor(url: String): Practices

    companion object {

        val UNKNOWN = Practices(2, Summary.UNKNOWN, emptyList(), emptyList())
    }
}
