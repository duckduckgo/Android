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

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.internal.RealPartnershipsHubUrlProvider.Companion.DEFAULT_URL
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class RealPartnershipsHubUrlProviderTest {

    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    private val moshi = Moshi.Builder().build()

    private val testee = RealPartnershipsHubUrlProvider(
        subscriptionsFeature = subscriptionsFeature,
        moshi = moshi,
    )

    @Test
    fun whenNoRemoteSettingsThenDefaultHubUrlUsed() {
        subscriptionsFeature.partnershipsHub().setRawStoredState(State(true))

        assertEquals(DEFAULT_URL, testee.partnershipsHubUrl)
    }

    @Test
    fun whenRemoteSettingsCarryUrlThenThatUrlUsed() {
        subscriptionsFeature.partnershipsHub().setRawStoredState(
            State(remoteEnableState = true, settings = """{"url":"https://example.com/hub"}"""),
        )

        assertEquals("https://example.com/hub", testee.partnershipsHubUrl)
    }

    @Test
    fun whenRemoteSettingsMalformedThenDefaultHubUrlUsed() {
        subscriptionsFeature.partnershipsHub().setRawStoredState(
            State(remoteEnableState = true, settings = "not json"),
        )

        assertEquals(DEFAULT_URL, testee.partnershipsHubUrl)
    }

    @Test
    fun whenRemoteSettingsMissingUrlKeyThenDefaultHubUrlUsed() {
        subscriptionsFeature.partnershipsHub().setRawStoredState(
            State(remoteEnableState = true, settings = """{"someOtherKey":"value"}"""),
        )

        assertEquals(DEFAULT_URL, testee.partnershipsHubUrl)
    }

    @Test
    fun whenRemoteUrlBlankThenDefaultHubUrlUsed() {
        subscriptionsFeature.partnershipsHub().setRawStoredState(
            State(remoteEnableState = true, settings = """{"url":"  "}"""),
        )

        assertEquals(DEFAULT_URL, testee.partnershipsHubUrl)
    }
}
