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

package com.duckduckgo.autofill.impl.ui.credential.management.survey

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject

interface AutofillSurveyJsonParser {
    suspend fun parseJson(json: String?): List<SurveyDetails>
}

@ContributesBinding(AppScope::class)
class AutofillSurveyJsonParserImpl @Inject constructor() : AutofillSurveyJsonParser {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    private fun buildJsonAdapter(): JsonAdapter<AutofillSettingsJson> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(AutofillSettingsJson::class.java)
    }

    override suspend fun parseJson(json: String?): List<SurveyDetails> {
        if (json == null) return emptyList()

        return kotlin.runCatching {
            val surveyDetailsJson = jsonAdapter.fromJson(json)
            return surveyDetailsJson?.surveys?.asSurveyDetails() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun List<SurveyDetailsJson>?.asSurveyDetails(): List<SurveyDetails> {
        if (this == null) return emptyList()
        return this.map { SurveyDetails(id = it.id, url = it.url) }
    }

    data class AutofillSettingsJson(
        val surveys: List<SurveyDetailsJson>,
    )

    data class SurveyDetailsJson(
        val id: String,
        val url: String,
    )
}
