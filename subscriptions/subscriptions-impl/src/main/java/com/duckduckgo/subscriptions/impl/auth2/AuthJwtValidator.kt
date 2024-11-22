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

package com.duckduckgo.subscriptions.impl.auth2

import java.time.Instant

/**
 * This class provides methods for checking the validity of JWTs obtained from Auth API V2 and parsing claims included in those tokens.
 */
interface AuthJwtValidator {
    /**
     * Validates access token against the given JSON Web Key (JWK) set and parses claims included in the token.
     *
     * @param jwt The access token to be validated, provided as a string.
     * @param jwkSet The JSON Web Key set containing cryptographic keys used to validate the token's signature.
     * @return [AccessTokenClaims] object that contains the claims from the validated token.
     * @throws IllegalArgumentException if the token or the JWK set is invalid or cannot be processed.
     */
    fun validateAccessToken(jwt: String, jwkSet: String): AccessTokenClaims

    /**
     * Validates refresh token against the given JSON Web Key (JWK) set and parses claims included in the token.
     *
     * @param jwt The refresh token to be validated, provided as a string.
     * @param jwkSet The JSON Web Key set containing cryptographic keys used to validate the token's signature.
     * @return [RefreshTokenClaims] object that contains the claims from the validated token.
     * @throws IllegalArgumentException if the token or the JWK set is invalid or cannot be processed.
     */
    fun validateRefreshToken(jwt: String, jwkSet: String): RefreshTokenClaims
}

data class AccessTokenClaims(
    /**
     * Timestamp for when the token will expire.
     */
    val expiresAt: Instant,

    /**
     * The external ID of the account authorized by this token.
     */
    val accountExternalId: String,

    /**
     * Email address associated with the account authorized by this token.
     */
    val email: String?,

    /**
     * Entitlements associated with the account authorized by this token.
     */
    val entitlements: List<Entitlement>,
)

data class RefreshTokenClaims(
    /**
     * Timestamp for when the token will expire.
     */
    val expiresAt: Instant,

    /**
     * The External ID of the account authorized by this token.
     */
    val accountExternalId: String,
)

data class Entitlement(
    /**
     * Name of the product represented by this entitlement.
     */
    val product: String,

    /**
     * Name of the entitlement.
     */
    val name: String,
)
