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

package com.duckduckgo.subscriptions.impl.internal

import android.annotation.SuppressLint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealPaywallPathProviderTest {

    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    private val moshi = Moshi.Builder().build()

    private val testee = RealPaywallPathProvider(
        subscriptionsFeature = subscriptionsFeature,
        moshi = moshi,
    )

    @Test
    fun whenSettingsCarryEntryPointsThenPathsAreReturned() {
        givenSettings(ENTRY_POINTS_SETTINGS)

        assertEquals("/subscriptions/new/mobile/vpn", testee.getPath("vpn"))
        assertEquals("/subscriptions/new/mobile/duckai", testee.getPath("duckai"))
        assertEquals("/subscriptions/new/mobile/pir", testee.getPath("pir"))
    }

    @Test
    fun whenFeatureDisabledThenPathIsStillReturned() {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(
            State(remoteEnableState = false, settings = ENTRY_POINTS_SETTINGS),
        )

        assertEquals("/subscriptions/new/mobile/vpn", testee.getPath("vpn"))
    }

    @Test
    fun whenNoSettingsThenNoPath() {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(State(remoteEnableState = true))

        assertNull(testee.getPath("vpn"))
    }

    @Test
    fun whenSettingsMalformedThenNoPath() {
        givenSettings("not json")

        assertNull(testee.getPath("vpn"))
    }

    @Test
    fun whenSettingsMissingEntryPointsKeyThenNoPath() {
        givenSettings("""{"someOtherKey":"value"}""")

        assertNull(testee.getPath("vpn"))
    }

    @Test
    fun whenFeaturePageHasNoEntryPointThenNoPath() {
        givenSettings("""{"entryPoints":{"vpn":{"path":"/subscriptions/new/mobile/vpn"}}}""")

        assertNull(testee.getPath("duckai"))
        assertNull(testee.getPath("itr"))
    }

    @Test
    fun whenPathBlankThenNoPath() {
        givenSettings("""{"entryPoints":{"vpn":{"path":"  "}}}""")

        assertNull(testee.getPath("vpn"))
    }

    @Test
    fun whenEntryPointHasNoPathThenNoPath() {
        givenSettings("""{"entryPoints":{"vpn":{}}}""")

        assertNull(testee.getPath("vpn"))
    }

    @Test
    fun whenPathIsConfiguredThenFeaturePageIsReturned() {
        givenSettings(ENTRY_POINTS_SETTINGS)

        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
        assertEquals("duckai", testee.getFeaturePage("/subscriptions/new/mobile/duckai"))
        assertEquals("pir", testee.getFeaturePage("/subscriptions/new/mobile/pir"))
    }

    @Test
    fun whenPathHasTrailingSlashThenFeaturePageIsReturned() {
        givenSettings(ENTRY_POINTS_SETTINGS)

        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn/"))
    }

    @Test
    fun whenConfiguredPathHasTrailingSlashThenItIsNormalized() {
        givenSettings("""{"entryPoints":{"vpn":{"path":"/subscriptions/new/mobile/vpn/"}}}""")

        assertEquals("/subscriptions/new/mobile/vpn", testee.getPath("vpn"))
        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn/"))
    }

    @Test
    fun whenConfiguredPathIsOnlyASlashThenItIsIgnored() {
        givenSettings("""{"entryPoints":{"vpn":{"path":"/"}}}""")

        assertNull(testee.getPath("vpn"))
        assertNull(testee.getFeaturePage("/"))
        assertNull(testee.getFeaturePage(""))
    }

    @Test
    fun whenPathIsNotConfiguredThenNoFeaturePage() {
        givenSettings(ENTRY_POINTS_SETTINGS)

        assertNull(testee.getFeaturePage("/subscriptions/new/mobile/itr"))
        assertNull(testee.getFeaturePage("/subscriptions"))
        assertNull(testee.getFeaturePage(""))
    }

    @Test
    fun whenFeatureDisabledThenFeaturePageIsStillReturned() {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(
            State(remoteEnableState = false, settings = ENTRY_POINTS_SETTINGS),
        )

        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
    }

    @Test
    fun whenNoSettingsThenNoFeaturePage() {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(State(remoteEnableState = true))

        assertNull(testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
    }

    @Test
    fun whenSettingsMalformedThenNoFeaturePage() {
        givenSettings("not json")

        assertNull(testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
    }

    @Test
    fun whenPathBlankThenNoFeaturePage() {
        givenSettings("""{"entryPoints":{"vpn":{"path":"  "}}}""")

        assertNull(testee.getFeaturePage("  "))
    }

    @Test
    fun whenSettingsChangeThenTheNewEntryPointsAreUsed() {
        givenSettings(ENTRY_POINTS_SETTINGS)
        assertEquals("/subscriptions/new/mobile/vpn", testee.getPath("vpn"))
        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn"))

        givenSettings("""{"entryPoints":{"vpn":{"path":"/subscriptions/new/mobile/vpn-v2"}}}""")

        assertEquals("/subscriptions/new/mobile/vpn-v2", testee.getPath("vpn"))
        assertEquals("vpn", testee.getFeaturePage("/subscriptions/new/mobile/vpn-v2"))
        assertNull(testee.getFeaturePage("/subscriptions/new/mobile/vpn"))
        assertNull(testee.getPath("duckai"))
    }

    private fun givenSettings(settings: String) {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(
            State(remoteEnableState = true, settings = settings),
        )
    }

    private companion object {
        val ENTRY_POINTS_SETTINGS = """
            {
                "entryPoints": {
                    "vpn": {
                        "path": "/subscriptions/new/mobile/vpn"
                    },
                    "duckai": {
                        "path": "/subscriptions/new/mobile/duckai"
                    },
                    "pir": {
                        "path": "/subscriptions/new/mobile/pir"
                    }
                }
            }
        """.trimIndent()
    }
}
