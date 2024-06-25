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

package com.duckduckgo.survey.api

interface SurveyParameterManager {
    /**
     * Builds the survey url, resolving the values for the ALL the required query parameters
     *
     * @return resolved survey url or `null` if not all query parameters can be provided
     */
    suspend fun buildSurveyUrlStrict(baseUrl: String, requestedQueryParams: List<String>): String?

    /**
     * Builds the survey url regardless if all required query parameters can be resolved
     *
     * @return resolved survey url where if a parameter could not be provided, it is set to empty string instead
     */
    suspend fun buildSurveyUrl(baseUrl: String, requestedQueryParams: List<String>): String
}
