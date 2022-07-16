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

import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.trackerdetection.EntityLookup
import javax.inject.Inject

class PrivacyPracticesImpl @Inject constructor(
    private val termsOfServiceStore: TermsOfServiceStore,
    private val entityLookup: EntityLookup
) : PrivacyPractices {

    private var entityScores: Map<String, Int> = mapOf()

    override suspend fun loadData() {
        val entityScores: MutableMap<String, Int> = mutableMapOf()

        termsOfServiceStore.terms.forEach {
            val url = it.name ?: return@forEach
            val derivedScore = it.derivedScore

            entityLookup.entityForUrl(url)?.let { entity ->
                val entityScore = entityScores[entity.name]
                if (entityScore == null || entityScore < derivedScore) {
                    entityScores[entity.name] = derivedScore
                }
            }
        }

        this.entityScores = entityScores
    }

    override fun privacyPracticesFor(url: String): Practices {
        val entity = entityLookup.entityForUrl(url)
        val terms = termsOfServiceStore.terms.find { UriString.sameOrSubdomain(url, it.name ?: "") } ?: return PrivacyPractices.UNKNOWN
        val score = entityScores[entity?.name] ?: terms.derivedScore
        return Practices(score, terms.practices, terms.goodPrivacyTerms, terms.badPrivacyTerms)
    }
}
