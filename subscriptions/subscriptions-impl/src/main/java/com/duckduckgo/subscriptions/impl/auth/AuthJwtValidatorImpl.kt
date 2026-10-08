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

package com.duckduckgo.subscriptions.impl.auth

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.model.Entitlement
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.ClaimsParseError
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Expired
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidAudience
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidClaim
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidIssuer
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidScope
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidSignature
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.JwksParseError
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.KeyNotFound
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Malformed
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.MissingClaim
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.NotYetValid
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Other
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Unsupported
import dev.zacsweers.metro.ContributesBinding
import io.jsonwebtoken.ClaimJwtException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.IncorrectClaimException
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.MissingClaimException
import io.jsonwebtoken.PrematureJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Jwks
import io.jsonwebtoken.security.SignatureException
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ContributesBinding(AppScope::class)
class AuthJwtValidatorImpl @Inject constructor(
    private val timeProvider: CurrentTimeProvider,
) : AuthJwtValidator {

    override fun validateAccessToken(
        jwt: String,
        jwkSet: String,
    ): AccessTokenClaims {
        val claims = parseSignedClaims(
            jwt = jwt,
            jwkSet = jwkSet,
            requiredAudience = "PrivacyPro",
            requiredScope = "privacypro",
        )

        return readClaims {
            AccessTokenClaims(
                expiresAt = claims.expiration.toInstant(),
                accountExternalId = claims.accountExternalId,
                email = claims.email,
                entitlements = claims.entitlements,
            )
        }
    }

    override fun validateRefreshToken(
        jwt: String,
        jwkSet: String,
    ): RefreshTokenClaims {
        val claims = parseSignedClaims(
            jwt = jwt,
            jwkSet = jwkSet,
            requiredAudience = "Auth",
            requiredScope = "refresh",
        )

        return readClaims {
            RefreshTokenClaims(
                expiresAt = claims.expiration.toInstant(),
                accountExternalId = claims.accountExternalId,
            )
        }
    }

    private fun parseSignedClaims(
        jwt: String,
        jwkSet: String,
        requiredAudience: String,
        requiredScope: String,
    ): Claims {
        val jwks = try {
            Jwks.setParser()
                .build()
                .parse(jwkSet)
                .getKeys()
        } catch (_: Exception) {
            throw TokenValidationException(JwksParseError)
        }

        val now = Instant.ofEpochMilli(timeProvider.currentTimeMillis())

        return try {
            Jwts.parser()
                .keyLocator { header ->
                    val keyId = (header as JwsHeader).keyId
                    jwks.first { it.id == keyId }.toKey()
                }
                .clock { Date.from(now) }
                .requireIssuer("https://quack.duckduckgo.com")
                .requireAudience(requiredAudience)
                .require("scope", requiredScope)
                .build()
                .parseSignedClaims(jwt)
                .payload
        } catch (e: Exception) {
            throw TokenValidationException(e.toFailureReason(now))
        }
    }

    private inline fun <T> readClaims(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        throw TokenValidationException(ClaimsParseError)
    }

    private fun Exception.toFailureReason(now: Instant): TokenValidationFailureReason = when (this) {
        is ExpiredJwtException -> Expired(clockSkew = clockSkew(now))
        is PrematureJwtException -> NotYetValid(clockSkew = clockSkew(now))
        is MissingClaimException -> MissingClaim
        is IncorrectClaimException -> when (claimName) {
            Claims.ISSUER -> InvalidIssuer
            Claims.AUDIENCE -> InvalidAudience
            "scope" -> InvalidScope
            else -> InvalidClaim
        }
        is SignatureException -> InvalidSignature
        is NoSuchElementException -> KeyNotFound
        is MalformedJwtException -> Malformed
        is UnsupportedJwtException -> Unsupported
        else -> Other
    }

    private fun ClaimJwtException.clockSkew(now: Instant): Duration? =
        claims?.issuedAt?.toInstant()?.let { issuedAt -> (now.toEpochMilli() - issuedAt.toEpochMilli()).milliseconds }

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
