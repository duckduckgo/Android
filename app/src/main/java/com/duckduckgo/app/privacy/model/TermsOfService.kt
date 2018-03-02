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

package com.duckduckgo.app.privacy.model

data class TermsOfService(val name: String? = null,
                          val score: Int = 0,
                          val classification: String? = null,
                          val goodPrivacyTerms: List<String> = ArrayList(),
                          val badPrivacyTerms: List<String> = ArrayList()) {

    enum class Practices {
        POOR,
        GOOD,
        MIXED,
        UNKNOWN
    }

    private val hasNoTerms: Boolean
        get() = goodPrivacyTerms.isEmpty() && badPrivacyTerms.isEmpty()

    private val hasMixedTerms: Boolean
        get() = !goodPrivacyTerms.isEmpty() && !badPrivacyTerms.isEmpty()

    val practices: Practices
        get() {
            if (hasNoTerms) {
                return Practices.UNKNOWN
            }
            when (classification) {
                "A" -> return Practices.GOOD
                "B" -> return Practices.MIXED
                "C", "D", "E" -> return Practices.POOR
            }
            if (hasMixedTerms) {
                return Practices.MIXED
            }
            when {
                score < 0 -> return Practices.GOOD
                score > 0 -> return Practices.POOR
                score == 0 -> return Practices.MIXED
            }
            return Practices.UNKNOWN
        }
}