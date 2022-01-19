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

package com.duckduckgo.mobile.android.vpn.bugreport

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DeviceInfoCollectorTest {

    @Mock private lateinit var appBuildConfig: AppBuildConfig

    private lateinit var deviceInfoCollector: DeviceInfoCollector

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        whenever(appBuildConfig.manufacturer).thenReturn("manufacturer")
        whenever(appBuildConfig.model).thenReturn("model")
        whenever(appBuildConfig.sdkInt).thenReturn(30)

        deviceInfoCollector = DeviceInfoCollector(appBuildConfig)
    }

    @Test
    fun whenCollectVpnRelatedStateThenReturnDeviceInfo() = runTest {
        val state = deviceInfoCollector.collectVpnRelatedState()

        assertEquals("deviceInfo", deviceInfoCollector.collectorName)

        assertEquals(4, state.length())
        assertEquals("PLAY", state.get("buildFlavor"))
        assertEquals("manufacturer", state.get("manufacturer"))
        assertEquals("model", state.get("model"))
        assertEquals(30, state.get("os"))
    }
}
