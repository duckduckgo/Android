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

package com.duckduckgo.subscriptions.impl.auth

import kotlin.time.Duration

class TokenValidationException(
    val reason: TokenValidationFailureReason,
) : IllegalArgumentException("Token validation failed: ${reason::class.simpleName}")

sealed class TokenValidationFailureReason {
    data class Expired(
        // Device time minus the token's `iat`; null if `iat` is missing
        val clockSkew: Duration?,
    ) : TokenValidationFailureReason()

    data class NotYetValid(
        // Device time minus the token's `iat`; null if `iat` is missing
        val clockSkew: Duration?,
    ) : TokenValidationFailureReason()

    data object InvalidSignature : TokenValidationFailureReason()
    data object KeyNotFound : TokenValidationFailureReason()
    data object JwksParseError : TokenValidationFailureReason()
    data object Malformed : TokenValidationFailureReason()
    data object Unsupported : TokenValidationFailureReason()
    data object MissingClaim : TokenValidationFailureReason()
    data object InvalidIssuer : TokenValidationFailureReason()
    data object InvalidAudience : TokenValidationFailureReason()
    data object InvalidScope : TokenValidationFailureReason()
    data object InvalidClaim : TokenValidationFailureReason()
    data object ClaimsParseError : TokenValidationFailureReason()
    data object Other : TokenValidationFailureReason()
}
