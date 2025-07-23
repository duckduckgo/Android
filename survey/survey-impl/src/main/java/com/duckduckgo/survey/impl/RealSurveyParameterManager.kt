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

package com.duckduckgo.survey.impl

import androidx.core.net.toUri
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.survey.api.SurveyParameterManager
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealSurveyParameterManager @Inject constructor(
    private val surveyParameterPluginPoint: PluginPoint<SurveyParameterPlugin>,
) : SurveyParameterManager {

    override suspend fun buildSurveyUrlStrict(
        baseUrl: String,
        requestedQueryParams: List<String>,
    ): String? {
        val urlBuilder = baseUrl.toUri().buildUpon()
        requestedQueryParams.forEach { param ->
            surveyParameterPluginPoint.getProviderForSurveyParamKey(param)?.let {
                urlBuilder.appendQueryParameter(param, it.evaluate(param))
            } ?: return null
        }
        return urlBuilder.build().toString()
    }

    override suspend fun buildSurveyUrl(
        baseUrl: String,
        requestedQueryParams: List<String>,
    ): String {
        val urlBuilder = baseUrl.toUri().buildUpon()
        requestedQueryParams.forEach {
            urlBuilder.appendQueryParameter(it, surveyParameterPluginPoint.getProviderForSurveyParamKey(it)?.evaluate(it) ?: "")
        }
        return urlBuilder.build().toString()
    }
}
