/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NetPRemoteFeatureWrapperTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val netPFeatureRemover: NetPFeatureRemover = mock()
    private lateinit var netPRemoteFeature: NetPRemoteFeature

    private lateinit var netPRemoteFeatureWrapper: NetPRemoteFeatureWrapper

    @Before
    fun setup() {
        netPRemoteFeature = FakeNetPRemoteFeatureFactory.create()

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        netPRemoteFeatureWrapper = NetPRemoteFeatureWrapper(
            netPRemoteFeature,
            netPFeatureRemover,
            appBuildConfig,
            coroutinesTestRule.testScope,
            coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `isWaitlistEnabled is enabled only when top and sub-feature are enabled`() {
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))

        assertFalse(netPRemoteFeatureWrapper.isWaitlistEnabled())

        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))

        assertFalse(netPRemoteFeatureWrapper.isWaitlistEnabled())

        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))

        assertFalse(netPRemoteFeatureWrapper.isWaitlistEnabled())

        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistEnabled())

        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = true))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistEnabled())

        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistEnabled())
    }

    @Test
    fun `isWaitlistActive always returns true for internal builds`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = true))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistActive())

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistActive())
    }

    @Test
    fun `isWaitlistActive always returns true if waitlist beta is active`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = true))

        assertTrue(netPRemoteFeatureWrapper.isWaitlistActive())

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertFalse(netPRemoteFeatureWrapper.isWaitlistActive())
    }

    @Test
    fun `isWaitlistActive always returns false if waitlist beta is not active and remove feature`() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertFalse(netPRemoteFeatureWrapper.isWaitlistActive())
        verify(netPFeatureRemover).removeFeature()
    }
}
