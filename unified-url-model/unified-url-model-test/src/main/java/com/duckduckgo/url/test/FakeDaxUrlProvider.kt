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

package com.duckduckgo.url.test

import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxDomainType
import com.duckduckgo.url.DaxScheme
import com.duckduckgo.url.DaxUrl
import com.duckduckgo.url.DaxUrlBuilder
import com.duckduckgo.url.DaxUrlProvider

/**
 * A pure-JVM [DaxUrlProvider] for unit tests in modules that consume the URL model.
 *
 * Consumers can't reach the real implementation: it lives in `:unified-url-model-impl` (it needs
 * `android.net.Uri`), and only `:app` may depend on an `-impl` module — including on a test
 * classpath. So a migrating module has no way to build a [DaxUrl]/[DaxDomain] for a test, and
 * mocking won't do either, because the real `sameOrSubdomain` casts its argument to its own type.
 * This is the same arrangement as `FakeFeatureToggleFactory` in `:feature-toggles-test`.
 *
 * Host normalization mirrors `Uri.baseHost` (scheme-optional parse, `www.` stripped, lowercased) for
 * the ordinary inputs a matching test uses. It is an approximation, not a second implementation:
 * percent-encoding, IDN, IPv6 and userinfo edge cases are not modelled, and [toTldPlusOne] uses the
 * last two labels because the public-suffix database isn't available here — so `sub.example.co.uk`
 * reduces to `co.uk`, not `example.co.uk`. Tests that turn on any of that belong in
 * `:unified-url-model-impl`, against the real implementation.
 */
class FakeDaxUrlProvider : DaxUrlProvider {

    override fun getUrl(url: String): DaxUrl = FakeDaxUrl(url)

    override fun getDomain(host: String): DaxDomain? = getUrl(host).domain

    override fun builder(): DaxUrlBuilder = FakeDaxUrlBuilder()

    override fun withScheme(
        url: DaxUrl,
        scheme: DaxScheme,
    ): DaxUrl = getUrl("${scheme.value}://${(url as FakeDaxUrl).authorityAndBelow}")

    override fun withScheme(
        url: String,
        scheme: DaxScheme,
    ): DaxUrl = withScheme(getUrl(url), scheme)
}

private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
private val PERCENT_ESCAPE = Regex("%([0-9a-fA-F]{2})")
private const val LOCALHOST = "localhost"

/**
 * Mirrors what `Uri` does when it reports a component: `getHost`/`getPath`/`getQuery` all hand back
 * decoded text. Consumers that re-encode on the way out (see `:malicious-site-protection-impl`) only
 * produce the same bytes as the real implementation if the fake decodes on the way in too.
 */
private fun String.percentDecoded(): String =
    PERCENT_ESCAPE.replace(this) { it.groupValues[1].toInt(16).toChar().toString() }

internal class FakeDaxUrl(private val raw: String) : DaxUrl {

    /** [raw] with any scheme removed, i.e. everything from the authority onwards. */
    internal val authorityAndBelow: String = raw.substringAfter("://", raw)

    private val authority: String = authorityAndBelow
        .takeWhile { it != '/' && it != '?' && it != '#' }
        .substringAfterLast('@')

    private val hostAndPort: String = authority

    override val domain: DaxDomain? = hostAndPort
        .substringBeforeLast(':', hostAndPort)
        .takeIf { it.isNotBlank() }
        ?.percentDecoded()
        ?.lowercase()
        ?.removePrefix("www.")
        ?.let { FakeDaxDomain(it) }

    override val host: String? = hostAndPort
        .substringBeforeLast(':', hostAndPort)
        .takeIf { it.isNotBlank() }
        ?.percentDecoded()

    override val scheme: DaxScheme = DaxScheme.fromValue(raw.substringBefore("://", "").ifBlank { null })

    override val isHttps: Boolean get() = scheme == DaxScheme.HTTPS

    override val isWebUrl: Boolean
        get() = domain != null && scheme in setOf(DaxScheme.HTTP, DaxScheme.HTTPS, DaxScheme.OTHER)

    override val isLocalUrl: Boolean
        get() = domain?.toString() == LOCALHOST || domain?.type == DaxDomainType.IP

    override val isMobileSite: Boolean
        get() = hostAndPort.startsWith("m.") || hostAndPort.startsWith("mobile.")

    override val path: String?
        get() = authorityAndBelow.dropWhile { it != '/' }.takeWhile { it != '?' && it != '#' }.takeIf { it.isNotEmpty() }?.percentDecoded()

    override val pathSegments: List<String>
        get() = path?.split('/')?.filter { it.isNotEmpty() }.orEmpty()

    override val lastPathSegment: String?
        get() = pathSegments.lastOrNull()

    override val port: Int?
        get() = hostAndPort.substringAfterLast(':', "").toIntOrNull()

    override val query: String?
        get() = raw.substringAfter('?', "").substringBefore('#').takeIf { it.isNotEmpty() }?.percentDecoded()

    override val fragment: String?
        get() = raw.substringAfter('#', "").takeIf { it.isNotEmpty() }

    private val queryParameters: Map<String, String>
        get() = query
            ?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.associate { it.substringBefore('=') to it.substringAfter('=', "") }
            .orEmpty()

    override val queryParameterNames: Set<String>
        get() = queryParameters.keys

    override fun queryParameter(key: String): String? = queryParameters[key]

    override fun matches(other: DaxUrl): Boolean = matches(other.domain ?: return false)

    override fun matches(other: DaxDomain): Boolean = other.sameOrSubdomain(domain ?: return false)

    override val serialized: String get() = raw
}

/**
 * String concatenation rather than a real URL assembler: it reproduces the shape the components
 * imply and nothing more. Components are taken verbatim, matching the [DaxUrlBuilder] contract; the
 * percent-encoding and path-normalization edge cases belong to the real implementation's tests.
 */
internal class FakeDaxUrlBuilder : DaxUrlBuilder {

    private var scheme: DaxScheme = DaxScheme.OTHER
    private var host: String? = null
    private var path: String? = null
    private var query: String? = null

    override fun scheme(scheme: DaxScheme): DaxUrlBuilder = apply { this.scheme = scheme }

    override fun encodedHost(host: String?): DaxUrlBuilder = apply { this.host = host }

    override fun encodedPath(path: String?): DaxUrlBuilder = apply { this.path = path }

    override fun encodedQuery(query: String?): DaxUrlBuilder = apply { this.query = query }

    override fun build(): DaxUrl = FakeDaxUrl(
        buildString {
            scheme.value.takeIf { it.isNotEmpty() }?.let { append(it).append("://") }
            host?.let { append(it) }
            path?.let { append(it) }
            query?.let { append('?').append(it) }
        },
    )
}

internal class FakeDaxDomain(private val value: String) : DaxDomain {

    override val type: DaxDomainType
        get() = when {
            value.matches(IPV4) -> DaxDomainType.IP
            value == LOCALHOST -> DaxDomainType.LOCALHOST
            value.contains('.') -> DaxDomainType.REGISTRABLE_DOMAIN
            else -> DaxDomainType.OTHER
        }

    // Compares through toString() rather than casting to FakeDaxDomain, so a test that ends up
    // holding a mix of fake and real domains gets the right answer instead of a ClassCastException.
    override fun sameOrSubdomain(child: DaxDomain): Boolean {
        val childValue = child.toString()
        return value == childValue || childValue.endsWith(".$value")
    }

    override fun toTldPlusOne(): DaxDomain? = value
        .split('.')
        .takeIf { it.size > 2 && type == DaxDomainType.REGISTRABLE_DOMAIN }
        ?.takeLast(2)
        ?.joinToString(".")
        ?.let { FakeDaxDomain(it) }

    override fun toTldPlusOneOrSelf(): DaxDomain = toTldPlusOne() ?: this

    override fun equals(other: Any?): Boolean = other is DaxDomain && other.toString() == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}
