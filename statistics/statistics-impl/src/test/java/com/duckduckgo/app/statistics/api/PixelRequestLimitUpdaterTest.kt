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

package com.duckduckgo.app.statistics.api

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class PixelRequestLimitUpdaterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val feature = FakeFeatureToggleFactory.create(PixelConcurrencyLimitFeature::class.java)
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = PixelRequestLimitUpdater(
        concurrencyLimitFeature = feature,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenCreatedThenDispatcherUsesDefaultLimit() {
        assertEquals(2, testee.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun whenAppCreatedThenRemoteLimitApplied() {
        givenRemoteLimit(1)

        testee.onCreate(lifecycleOwner)

        assertEquals(1, testee.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun whenPrivacyConfigDownloadedThenNewRemoteLimitApplied() {
        givenRemoteLimit(1)
        testee.onCreate(lifecycleOwner)

        givenRemoteLimit(3)
        testee.onPrivacyConfigDownloaded()

        assertEquals(3, testee.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun whenVpnProcessCreatedThenRemoteLimitApplied() {
        givenRemoteLimit(1)

        testee.onVpnProcessCreated()

        assertEquals(1, testee.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun whenPirProcessCreatedThenRemoteLimitApplied() {
        givenRemoteLimit(1)

        testee.onPirProcessCreated()

        assertEquals(1, testee.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun whenNoRemoteSettingsThenDefaultLimitKept() {
        testee.onCreate(lifecycleOwner)

        assertEquals(2, testee.dispatcher.maxRequestsPerHost)
    }

    private fun givenRemoteLimit(limit: Int) {
        feature.self().setRawStoredState(Toggle.State(enable = true, settings = """{"maxRequestsPerHost": $limit}"""))
    }
}
