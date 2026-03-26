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

data class PrivacyPassCredential(
    val credentialId: String,
    val credits: Int,
)

data class PrivacyPassSpendResult(
    val credentialId: String,
    val remainingCredits: Int,
    val token: String,
)

data class PrivacyPassBalanceResult(
    val credentialId: String,
    val credits: Int,
)

data class PrivacyPassRedeemResult(
    val success: Boolean,
    val message: String,
)

interface PrivacyPassManager {
    suspend fun issueCredential(issuer: String, credits: Int): PrivacyPassCredential
    suspend fun spendCredits(credentialId: String, amount: Int): PrivacyPassSpendResult
    suspend fun balance(credentialId: String): PrivacyPassBalanceResult
    suspend fun redeemToken(token: String): PrivacyPassRedeemResult
}
