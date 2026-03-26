/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.privacypass.api

sealed class PrivacyPassResult {
    data class Success(val authorizationHeader: String) : PrivacyPassResult()
    data class Failure(val reason: String) : PrivacyPassResult()
}

data class PrivacyPassChallenge(
    val tokenType: Int,
    val issuerUrl: String,
    val challenge: String,
    val tokenKey: String,
)

interface PrivacyPassManager {

    fun isPrivateTokenChallenge(statusCode: Int, headers: Map<String, String>): Boolean

    suspend fun handlePrivateTokenChallenge(
        originalUrl: String,
        wwwAuthenticateHeader: String,
    ): PrivacyPassResult

    fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge?
}
