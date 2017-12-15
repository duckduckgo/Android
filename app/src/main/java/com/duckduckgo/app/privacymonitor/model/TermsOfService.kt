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

package com.duckduckgo.app.privacymonitor.model

import android.support.annotation.IntDef

data class TermsOfService(val name: String? = null,
                          val score: Int = 0,
                          val classification: String? = null,
                          val goodPrivacyTerms: List<String> = ArrayList(),
                          val badPrivacyTerms: List<String> = ArrayList()) {

    companion object {
        @IntDef(POOR, GOOD, MIXED)
        @Retention(AnnotationRetention.SOURCE)
        annotation class Practices

        const val POOR = 0L
        const val GOOD = 1L
        const val MIXED = 2L
        const val UNKNOWN = 3L
    }

    private val noTerms: Boolean
        get() = goodPrivacyTerms.isEmpty() && badPrivacyTerms.isEmpty()

    private val mixedTerms: Boolean
        get() = !goodPrivacyTerms.isEmpty() && !badPrivacyTerms.isEmpty()

    val practices: Long
        get() {
            if (noTerms) {
                return UNKNOWN
            }
            when (classification) {
                "A" -> return GOOD
                "B" -> return MIXED
                "C", "D", "E" -> return POOR
            }
            if (mixedTerms) {
                return MIXED
            }
            when {
                score < 0 -> return GOOD
                score > 0 -> return POOR
                score == 0 -> return MIXED
            }
            return UNKNOWN
        }
}