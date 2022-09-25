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

import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*

data class TermsOfService(
    val name: String? = null,
    val score: Int = 0,
    val classification: String? = null,
    val goodPrivacyTerms: List<String> = ArrayList(),
    val badPrivacyTerms: List<String> = ArrayList()
) {

    val practices: PrivacyPractices.Summary
        get() {

            when (classification) {

                "A" -> return GOOD
                "B" -> return MIXED
                "C" -> return POOR
                "D" -> return POOR

            }

            if (goodPrivacyTerms.isNotEmpty() && badPrivacyTerms.isNotEmpty()) {
                return MIXED
            }

            if (score < 0) {
                return GOOD
            } else if (score == 0 && (goodPrivacyTerms.isNotEmpty() || badPrivacyTerms.isNotEmpty())) {
                return MIXED
            } else if (score > 0) {
                return POOR
            }

            return UNKNOWN
        }

    val derivedScore: Int
        get() {
            var derived = 5

            // assign a score value to the classes/scores provided in the JSON file
            if (classification == "A") {
                derived = 0
            } else if (classification == "B") {
                derived = 1
            } else if (classification == "D" || score > 150) {
                derived = 10
            } else if (classification == "C" || score > 100) {
                derived = 7
            }

            return derived
        }
}
