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

package com.duckduckgo.deviceauth.impl

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealDeviceAuthenticatorTest {

    private lateinit var testee: RealDeviceAuthenticator

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    @Mock
    private lateinit var deviceAuthChecker: SupportedDeviceAuthChecker

    @Mock
    private lateinit var authLauncher: AuthLauncher

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealDeviceAuthenticator(
            deviceAuthChecker,
            appBuildConfig,
            authLauncher
        )
    }

    @Test
    fun whenSdkIs28ThenValidAuthenticationShouldCheckLegacy() {
        whenever(appBuildConfig.sdkInt).thenReturn(28)

        testee.hasValidDeviceAuthentication()

        verify(deviceAuthChecker).supportsLegacyAuthentication()
    }

    @Test
    fun whenSdkIs29ThenValidAuthenticationShouldCheckLegacy() {
        whenever(appBuildConfig.sdkInt).thenReturn(29)

        testee.hasValidDeviceAuthentication()

        verify(deviceAuthChecker).supportsLegacyAuthentication()
    }

    @Test
    fun whenSdkIsNot28Or29ThenValidAuthenticationShouldCheckLegacy() {
        whenever(appBuildConfig.sdkInt).thenReturn(30)

        testee.hasValidDeviceAuthentication()

        verify(deviceAuthChecker).supportsStrongAuthentication()
    }

    @Test
    fun whenAuthenticateIsCalledWithFragmentThenLaunchAuthLauncher() {
        val fragment: Fragment = mock()

        testee.authenticate(AUTOFILL, fragment) {}

        verify(authLauncher).launch(eq(R.string.autofill_auth_text), eq(fragment), any())
    }

    @Test
    fun whenAuthenticateIsCalledWithActivityThenLaunchAuthLauncher() {
        val fragment: FragmentActivity = mock()

        testee.authenticate(AUTOFILL, fragment) {}

        verify(authLauncher).launch(eq(R.string.autofill_auth_text), eq(fragment), any())
    }
}
