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

package com.duckduckgo.feature.toggles.fakes

import com.duckduckgo.feature.toggles.api.Toggle.State.Target
import com.duckduckgo.feature.toggles.internal.api.FeatureTogglesCallback
import java.util.*

class FakeFeatureTogglesCallback constructor() : FeatureTogglesCallback {
    var locale = Locale.US
    var isReturningUser = true
    var isPrivacyProEligible = false
    var sdkVersion = 28

    override fun onCohortAssigned(
        experimentName: String,
        cohortName: String,
        enrollmentDate: String,
    ) {
        // no-op
    }

    override fun matchesToggleTargets(targets: List<Any>): Boolean {
        if (targets.isEmpty()) return true

        targets.forEach { target ->
            if (target is Target) {
                if (matchTarget(target)) {
                    return true
                }
            } else {
                throw RuntimeException("targets shall be of type Toggle.State.Target")
            }
        }

        return false
    }

    private fun matchTarget(target: Target): Boolean {
        fun matchLocale(targetCountry: String?, targetLanguage: String?): Boolean {
            val countryMatches = targetCountry?.let { locale.country.lowercase() == targetCountry.lowercase() } ?: true
            val languageMatches = targetLanguage?.let { locale.language.lowercase() == targetLanguage.lowercase() } ?: true

            return countryMatches && languageMatches
        }

        fun matchReturningUser(targetIsReturningUser: Boolean?): Boolean {
            return targetIsReturningUser?.let { targetIsReturningUser == isReturningUser } ?: true
        }

        fun matchPrivacyProEligible(targetPProEligible: Boolean?): Boolean {
            return targetPProEligible?.let { targetPProEligible == isPrivacyProEligible } ?: true
        }

        fun matchMinSdkVersion(targetMinSdkVersion: Int?): Boolean {
            return targetMinSdkVersion?.let { targetMinSdkVersion <= sdkVersion } ?: true
        }

        return matchLocale(target.localeCountry, target.localeLanguage) &&
            matchPrivacyProEligible(target.isPrivacyProEligible) &&
            matchReturningUser(target.isReturningUser) &&
            matchMinSdkVersion(target.minSdkVersion)
    }
}
