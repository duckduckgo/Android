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

package com.duckduckgo.authjwt.impl

import com.duckduckgo.authjwt.api.AccessTokenClaims
import com.duckduckgo.authjwt.api.AuthJwt
import com.duckduckgo.authjwt.api.Entitlement
import com.duckduckgo.authjwt.api.RefreshTokenClaims
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Jwks
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class AuthJwtImpl @Inject constructor() : AuthJwt {

    override fun validateAccessToken(
        jwt: String,
        jwkSet: String,
    ): AccessTokenClaims {
        try {
            val claims = parseSignedClaims(jwt, jwkSet)
            return AccessTokenClaims(
                expiresAt = claims.expiration.toInstant(),
                accountExternalId = claims.accountExternalId,
                email = claims.email,
                entitlements = claims.entitlements,
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Access token validation failed")
        }
    }

    override fun validateRefreshToken(
        jwt: String,
        jwkSet: String,
    ): RefreshTokenClaims {
        try {
            val claims = parseSignedClaims(jwt, jwkSet)
            return RefreshTokenClaims(
                expiresAt = claims.expiration.toInstant(),
                accountExternalId = claims.accountExternalId,
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Refresh token validation failed")
        }
    }

    private fun parseSignedClaims(
        jwt: String,
        jwkSet: String,
    ): Claims {
        val jwks = Jwks.setParser()
            .build()
            .parse(jwkSet)
            .getKeys()

        return Jwts.parser()
            .keyLocator { header ->
                val keyId = (header as JwsHeader).keyId
                jwks.first { it.id == keyId }.toKey()
            }
            .build()
            .parseSignedClaims(jwt)
            .payload
    }

    private val Claims.accountExternalId: String
        get() = getTypedValue("sub")

    private val Claims.email: String
        get() = getTypedValue("email")

    private val Claims.entitlements: List<Entitlement>
        get() = getTypedValue<List<Map<String, Any?>>>("entitlements")
            .map { entitlementProperties ->
                Entitlement(
                    name = entitlementProperties["name"] as String,
                    product = entitlementProperties["product"] as String,
                )
            }

    private inline fun <reified T> Claims.getTypedValue(claimName: String): T = get(claimName, T::class.java)
}
