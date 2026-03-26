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

package com.duckduckgo.privacypass.impl

import com.squareup.anvil.annotations.ContributesBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacypass.api.PrivacyPassBalanceResult
import com.duckduckgo.privacypass.api.PrivacyPassCredential
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.duckduckgo.privacypass.api.PrivacyPassRedeemResult
import com.duckduckgo.privacypass.api.PrivacyPassSpendResult
import dagger.SingleInstanceIn
import logcat.logcat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyPassManager @Inject constructor() : PrivacyPassManager {

    private val credentials = ConcurrentHashMap<String, Int>()
    private val redeemedTokens = ConcurrentHashMap<String, String>()

    override suspend fun issueCredential(issuer: String, credits: Int): PrivacyPassCredential {
        val credentialId = UUID.randomUUID().toString()
        credentials[credentialId] = credits
        logcat { "PrivacyPass: issued credential $credentialId with $credits credits from issuer=$issuer" }
        return PrivacyPassCredential(credentialId = credentialId, credits = credits)
    }

    override suspend fun spendCredits(credentialId: String, amount: Int): PrivacyPassSpendResult {
        val currentCredits = credentials[credentialId]
            ?: throw IllegalArgumentException("Unknown credential: $credentialId")

        if (amount > currentCredits) {
            throw IllegalArgumentException("Insufficient credits: requested=$amount, available=$currentCredits")
        }

        val remaining = currentCredits - amount
        credentials[credentialId] = remaining
        val token = UUID.randomUUID().toString()
        redeemedTokens[token] = credentialId

        logcat { "PrivacyPass: spent $amount credits from $credentialId, remaining=$remaining, token=$token" }
        return PrivacyPassSpendResult(credentialId = credentialId, remainingCredits = remaining, token = token)
    }

    override suspend fun balance(credentialId: String): PrivacyPassBalanceResult {
        val currentCredits = credentials[credentialId]
            ?: throw IllegalArgumentException("Unknown credential: $credentialId")

        return PrivacyPassBalanceResult(credentialId = credentialId, credits = currentCredits)
    }

    override suspend fun redeemToken(token: String): PrivacyPassRedeemResult {
        val credentialId = redeemedTokens.remove(token)
        return if (credentialId != null) {
            logcat { "PrivacyPass: redeemed token=$token for credential=$credentialId" }
            PrivacyPassRedeemResult(success = true, message = "Token redeemed successfully")
        } else {
            logcat { "PrivacyPass: failed to redeem unknown token=$token" }
            PrivacyPassRedeemResult(success = false, message = "Unknown or already redeemed token")
        }
    }
}
