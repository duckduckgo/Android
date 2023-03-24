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
import com.duckduckgo.deviceauth.api.AutofillAuthorizationGracePeriod
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL_TO_ACCESS_CREDENTIALS
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL_TO_USE_CREDENTIALS
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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

    @Mock
    private lateinit var autofillAuthorizationGracePeriod: AutofillAuthorizationGracePeriod

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var fragmentActivity: FragmentActivity

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealDeviceAuthenticator(
            deviceAuthChecker,
            appBuildConfig,
            authLauncher,
            autofillAuthorizationGracePeriod,
        )
        whenever(autofillAuthorizationGracePeriod.isAuthRequired()).thenReturn(true)
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
    fun whenAuthenticateToAccessCredentialsIsCalledWithFragmentThenLaunchAuthLauncher() {
        testee.authenticate(AUTOFILL_TO_ACCESS_CREDENTIALS, fragment) {}

        verify(authLauncher).launch(eq(R.string.autofill_auth_text_for_access), eq(fragment), any())
    }

    @Test
    fun whenAuthenticateToUseCredentialsIsCalledWithFragmentThenLaunchAuthLauncher() {
        testee.authenticate(AUTOFILL_TO_USE_CREDENTIALS, fragment) {}
        verify(authLauncher).launch(eq(R.string.autofill_auth_text_for_using), eq(fragment), any())
    }

    @Test
    fun whenAuthenticateToAccessCredentialsIsCalledWithActivityThenLaunchAuthLauncher() {
        testee.authenticate(AUTOFILL_TO_ACCESS_CREDENTIALS, fragment) {}
        verify(authLauncher).launch(eq(R.string.autofill_auth_text_for_access), eq(fragment), any())
    }

    @Test
    fun whenAuthenticateToUseCredentialsIsCalledWithActivityThenLaunchAuthLauncher() {
        testee.authenticate(AUTOFILL_TO_USE_CREDENTIALS, fragment) {}
        verify(authLauncher).launch(eq(R.string.autofill_auth_text_for_using), eq(fragment), any())
    }

    @Test
    fun whenAuthGracePeriodActiveThenNoDeviceAuthLaunchedWhenUsingCredentials() {
        whenever(autofillAuthorizationGracePeriod.isAuthRequired()).thenReturn(false)
        testee.authenticate(AUTOFILL_TO_USE_CREDENTIALS, fragmentActivity) {}
        verifyAuthNotLaunched()
    }

    @Test
    fun whenAuthGracePeriodActiveThenNoDeviceAuthLaunchedWhenAccessingCredentials() {
        whenever(autofillAuthorizationGracePeriod.isAuthRequired()).thenReturn(false)
        testee.authenticate(AUTOFILL_TO_ACCESS_CREDENTIALS, fragmentActivity) {}
        verifyAuthNotLaunched()
    }

    private fun verifyAuthNotLaunched() {
        verify(authLauncher, never()).launch(any(), any<Fragment>(), any())
        verify(authLauncher, never()).launch(any(), any<FragmentActivity>(), any())
    }
}
