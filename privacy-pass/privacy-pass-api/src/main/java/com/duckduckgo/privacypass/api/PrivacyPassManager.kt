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
    val tokenKey: String? = null,
    val redemptionContext: ByteArray? = null,
    val rawTokenChallenge: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivacyPassChallenge) return false
        return tokenType == other.tokenType && issuerUrl == other.issuerUrl && challenge == other.challenge &&
            tokenKey == other.tokenKey
    }

    override fun hashCode(): Int {
        var result = tokenType
        result = 31 * result + issuerUrl.hashCode()
        result = 31 * result + challenge.hashCode()
        result = 31 * result + (tokenKey?.hashCode() ?: 0)
        return result
    }
}

interface PrivacyPassManager {

    fun isPrivateTokenChallenge(statusCode: Int, headers: Map<String, String>): Boolean

    suspend fun handlePrivateTokenChallenge(
        originalUrl: String,
        wwwAuthenticateHeader: String,
    ): PrivacyPassResult

    fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge?
}
