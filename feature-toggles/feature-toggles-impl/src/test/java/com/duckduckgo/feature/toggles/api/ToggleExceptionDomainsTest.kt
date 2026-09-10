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

import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.internal.CachedToggleStore
import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxDomainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ToggleExceptionDomainsTest {

    private lateinit var toggleStore: Toggle.Store
    private lateinit var feature: TestFeature
    private var resolveCount = 0

    @Before
    fun setup() {
        resolveCount = 0
        toggleStore = CachedToggleStore(FakeToggleStore())
        feature = FeatureToggles.Builder(
            store = toggleStore,
            featureName = "test",
            daxDomainResolver = { host ->
                resolveCount++
                host.takeIf { it.isNotBlank() }?.let { FakeDaxDomain(it) }
            },
        )
            .build()
            .create(TestFeature::class.java)
    }

    @Test
    fun whenExceptionsPresentThenDomainsAreResolved() {
        givenExceptions("example.com", "other.com")

        assertEquals(
            listOf(FakeDaxDomain("example.com"), FakeDaxDomain("other.com")),
            feature.self().getExceptionDomains(),
        )
    }

    @Test
    fun whenNoStateThenDomainsAreEmpty() {
        assertEquals(emptyList<DaxDomain>(), feature.self().getExceptionDomains())
    }

    @Test
    fun whenDomainHasNoResolvableHostThenItIsDropped() {
        givenExceptions("example.com", "", "other.com")

        assertEquals(
            listOf(FakeDaxDomain("example.com"), FakeDaxDomain("other.com")),
            feature.self().getExceptionDomains(),
        )
    }

    @Test
    fun whenReadTwiceThenDomainsAreResolvedOnce() {
        givenExceptions("example.com")

        val first = feature.self().getExceptionDomains()
        assertSame(first, feature.self().getExceptionDomains())
        assertEquals(1, resolveCount)
    }

    @Test
    fun whenConfigChangesThenDomainsAreResolvedAgain() {
        givenExceptions("example.com")
        val first = feature.self().getExceptionDomains()

        givenExceptions("changed.com")
        val second = feature.self().getExceptionDomains()

        assertNotSame(first, second)
        assertEquals(listOf(FakeDaxDomain("changed.com")), second)
    }

    private fun givenExceptions(vararg domains: String) {
        feature.self().setRawStoredState(
            Toggle.State(exceptions = domains.map { FeatureException(it, reason = null) }),
        )
    }

    interface TestFeature {
        @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
        fun self(): Toggle
    }

    private data class FakeDaxDomain(val host: String) : DaxDomain {
        override val type = DaxDomainType.REGISTRABLE_DOMAIN
        override fun sameOrSubdomain(child: DaxDomain) = child == this
        override fun toTldPlusOne() = this
        override fun toTldPlusOneOrSelf() = this
    }
}
