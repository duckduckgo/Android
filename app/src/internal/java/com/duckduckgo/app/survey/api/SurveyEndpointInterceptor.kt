/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.survey.api

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class SurveyEndpointInterceptor @Inject constructor(
    private val surveyEndpointDataStore: SurveyEndpointDataStore,
) : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this
    override fun intercept(chain: Chain): Response {
        val useCustomUrl = surveyEndpointDataStore.useSurveyCustomEnvironmentUrl
        val url = chain.request().url
        val encodedPath = chain.request().url.encodedPath
        val scheme = chain.request().url.scheme

        if (useCustomUrl && url.toString().contains(SURVEY_HOST)) {
            val newRequest = chain.request().newBuilder()

            val changedUrl = "$scheme://$SURVEY_SANDBOX_HOST$encodedPath"
            logcat(VERBOSE) { "Survey endpoint changed to $changedUrl" }
            newRequest.url(changedUrl)
            return chain.proceed(newRequest.build())
        }

        return chain.proceed(chain.request())
    }

    companion object {
        private const val SURVEY_HOST = "https://staticcdn.duckduckgo.com/survey"
        private const val SURVEY_SANDBOX_HOST = "ddg-sandbox.s3.amazonaws.com"
    }
}
