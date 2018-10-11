/*
 * Copyright (c) 2018 DuckDuckGo
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

import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyPractices @Inject constructor(
    private val termsOfServiceStore: TermsOfServiceStore,
    private val entityMapping: EntityMapping) {

    enum class Summary {
        POOR,
        GOOD,
        MIXED,
        UNKNOWN
    }

    data class Practices(val score: Int, val summary: Summary, val goodReasons: List<String>, val badReasons: List<String>)

    private var entityScores: Map<String, Int> = mapOf()

    init {
        refreshScores()
    }

    fun refreshScores() {
        val entityScores: MutableMap<String, Int> = mutableMapOf()

        termsOfServiceStore.terms.forEach {
            val url = it.name ?: return@forEach
            val derivedScore = it.derivedScore

            entityMapping.entityForUrl(url)?.let {

                val entityScore = entityScores[it.entityName]
                if (entityScore == null || entityScore < derivedScore) {
                    entityScores[it.entityName] = derivedScore
                }

            }
        }

        this.entityScores = entityScores
    }

    fun privacyPracticesFor(url: String): Practices {
        val entity = entityMapping.entityForUrl(url)
        val terms = termsOfServiceStore.terms.find { sameOrSubdomain(url, it.name ?: "") } ?: return UNKNOWN
        val score = entityScores[entity?.entityName] ?: terms.derivedScore
        return Practices(score, toPractices(terms.practices), terms.goodPrivacyTerms, terms.badPrivacyTerms)
    }

    // TODO remove
    private fun toPractices(practices: TermsOfService.Practices): Summary {
        when(practices) {
            TermsOfService.Practices.GOOD -> return Summary.GOOD
            TermsOfService.Practices.POOR -> return Summary.POOR
            TermsOfService.Practices.MIXED -> return Summary.MIXED
            TermsOfService.Practices.UNKNOWN -> return Summary.UNKNOWN
        }
    }

    companion object {

        val UNKNOWN = Practices(2, Summary.UNKNOWN, emptyList(), emptyList())

    }

}