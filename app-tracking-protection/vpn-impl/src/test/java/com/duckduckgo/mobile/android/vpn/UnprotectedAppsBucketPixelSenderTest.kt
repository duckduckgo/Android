/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.exclusion.AppCategory
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UnprotectedAppsBucketPixelSenderTest {

    private val mockTrackingProtectionAppsRepository = mock<TrackingProtectionAppsRepository>()
    private val mockDeviceShieldPixels = mock<DeviceShieldPixels>()
    private val protectedAppsChannel = Channel<List<TrackingProtectionAppInfo>>(1, BufferOverflow.DROP_LATEST)

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: UnprotectedAppsBucketPixelSender

    @Before
    fun setup() {
        testee = UnprotectedAppsBucketPixelSender(
            mockTrackingProtectionAppsRepository,
            mockDeviceShieldPixels,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenVpnStartedWithNoUnprotectedAppsThenSentPixelWithBucketSize20() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, app, app, app, app, app, app))
        val expectedBucketSize = 20

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWith1UnprotectedAppsOutOf7ThenSentPixelWithBucketSize20() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, app, app, app, app, excludedApp, app))
        val expectedBucketSize = 20

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWith2UnprotectedAppsOutOf7ThenSentPixelWithBucketSize40() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, app, app, app, app, excludedApp, excludedApp))
        val expectedBucketSize = 40

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWith4UnprotectedAppsOutOf7ThenSentPixelWithBucketSize60() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, app, excludedApp, app, excludedApp, excludedApp, excludedApp))
        val expectedBucketSize = 60

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWith5UnprotectedAppsOutOf7ThenSentPixelWithBucketSize80() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, app, excludedApp, excludedApp, excludedApp, excludedApp, excludedApp))
        val expectedBucketSize = 80

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWith6UnprotectedAppsOutOf7ThenSentPixelWithBucketSize100() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(app, excludedApp, excludedApp, excludedApp, excludedApp, excludedApp, excludedApp))
        val expectedBucketSize = 100

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    @Test
    fun whenVpnStartedWithAllAppsUnprotectedThenSentPixelWithBucketSize100() = runTest {
        whenever(mockTrackingProtectionAppsRepository.getAppsAndProtectionInfo()).thenReturn(protectedAppsChannel.receiveAsFlow())
        protectedAppsChannel.send(listOf(excludedApp, excludedApp, excludedApp, excludedApp, excludedApp, excludedApp, excludedApp))
        val expectedBucketSize = 100

        testee.onVpnStarted(coroutineRule.testScope)

        verify(mockDeviceShieldPixels).reportUnprotectedAppsBucket(expectedBucketSize)
    }

    private val app = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        category = AppCategory.Undefined,
        isExcluded = false,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModified = false,
    )

    private val excludedApp = TrackingProtectionAppInfo(
        packageName = "com.package.name",
        name = "App",
        category = AppCategory.Undefined,
        isExcluded = true,
        knownProblem = TrackingProtectionAppInfo.NO_ISSUES,
        userModified = false,
    )
}
