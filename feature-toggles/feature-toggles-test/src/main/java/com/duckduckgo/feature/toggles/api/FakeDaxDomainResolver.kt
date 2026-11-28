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

package com.duckduckgo.feature.toggles.api

import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxDomainType
import okhttp3.internal.publicsuffix.PublicSuffixDatabase

/**
 * Host-only [DaxDomain] resolution for JVM unit tests.
 *
 * `RealDaxUrlProvider` can't be used here: it resolves through `android.net.Uri` and lives in
 * `:unified-url-model-impl`, which no test fixture may depend on. Exception domains from remote config are bare
 * hosts, so full URL parsing isn't needed. Normalization (lowercase, then `www.` stripped), `sameOrSubdomain` and
 * the eTLD+1 lookup mirror `RealDaxDomain` and must stay in sync with it.
 *
 * Internationalized hosts are not converted to Punycode: the real conversion uses Android's ICU, which a plain
 * JVM module can't reach, and `java.net.IDN` converts differently (`faß.de` → `fass.de`). Tests that depend on
 * IDN equality belong in `:unified-url-model-impl`.
 */
fun fakeDaxDomainResolver(host: String): DaxDomain? =
    host.lowercase().removePrefix("www.").takeIf { it.isNotBlank() }?.let(::FakeDaxDomain)

private class FakeDaxDomain(private val value: String) : DaxDomain {

    private val dottedValue = ".$value"

    override fun sameOrSubdomain(child: DaxDomain): Boolean {
        val childValue = (child as FakeDaxDomain).value
        return value == childValue || childValue.endsWith(dottedValue)
    }

    override fun toTldPlusOne(): DaxDomain? = registrableDomain?.let(::FakeDaxDomain)

    override fun toTldPlusOneOrSelf(): DaxDomain = toTldPlusOne() ?: this

    override val type: DaxDomainType
        get() = when {
            value.isIpv4Literal() -> DaxDomainType.IP
            value == "localhost" -> DaxDomainType.LOCALHOST
            registrableDomain != null -> DaxDomainType.REGISTRABLE_DOMAIN
            else -> DaxDomainType.OTHER
        }

    override fun equals(other: Any?): Boolean = other is FakeDaxDomain && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    private val registrableDomain: String?
        get() = if (value.isIpv4Literal()) null else PublicSuffixDatabase.get().getEffectiveTldPlusOne(value)

    private fun String.isIpv4Literal(): Boolean {
        val octets = split('.')
        if (octets.size != 4) return false
        return octets.all { octet -> octet.isNotEmpty() && octet.all { it.isDigit() } && octet.toInt() in 0..255 }
    }
}
