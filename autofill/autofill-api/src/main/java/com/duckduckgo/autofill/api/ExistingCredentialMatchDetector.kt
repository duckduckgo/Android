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

package com.duckduckgo.autofill.api

/**
 * Used to determine if the given credential details exist in the autofill storage
 *
 * There are times when the UI from the main app will need to prompt the user if they want to update saved details.
 * We can only show that prompt if we've first determined there is an existing partial match in need of an update.
 */
interface ExistingCredentialMatchDetector {

    /**
     * Determine if the given credential exists in the autofill storage.
     * This isn't a binary, as there are different match types that can be returned as captured by [ContainsCredentialsResult]
     */
    suspend fun determine(currentUrl: String, username: String?, password: String?): ContainsCredentialsResult

    /**
     * Possible match types returned when searching for the presence of credentials
     */
    sealed interface ContainsCredentialsResult {
        data object ExactMatch : ContainsCredentialsResult
        data object UsernameMatchDifferentPassword : ContainsCredentialsResult
        data object UsernameMatchMissingPassword : ContainsCredentialsResult
        data object UrlOnlyMatch : ContainsCredentialsResult
        data object UsernameMissing : ContainsCredentialsResult
        data object NoMatch : ContainsCredentialsResult
    }
}
