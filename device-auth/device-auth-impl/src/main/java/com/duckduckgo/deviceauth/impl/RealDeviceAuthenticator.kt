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

import android.os.Build
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.deviceauth.api.AutofillAuthorizationGracePeriod
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDeviceAuthenticator @Inject constructor(
    private val deviceAuthChecker: SupportedDeviceAuthChecker,
    private val appBuildConfig: AppBuildConfig,
    private val authLauncher: AuthLauncher,
    private val autofillAuthGracePeriod: AutofillAuthorizationGracePeriod,
) : DeviceAuthenticator {

    override fun hasValidDeviceAuthentication(): Boolean {
        // https://developer.android.com/reference/androidx/biometric/BiometricManager#canAuthenticate(int)
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 28-29
        return if (appBuildConfig.sdkInt != Build.VERSION_CODES.Q && appBuildConfig.sdkInt != Build.VERSION_CODES.P) {
            deviceAuthChecker.supportsStrongAuthentication()
        } else {
            deviceAuthChecker.supportsLegacyAuthentication()
        }
    }

    @UiThread
    override fun authenticate(
        featureToAuth: Features,
        fragment: Fragment,
        onResult: (AuthResult) -> Unit,
    ) {
        if (autofillAuthGracePeriod.isAuthRequired()) {
            authLauncher.launch(getAuthText(featureToAuth), fragment, onResult)
        } else {
            onResult(AuthResult.Success)
        }
    }

    @UiThread
    override fun authenticate(
        featureToAuth: Features,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    ) {
        if (autofillAuthGracePeriod.isAuthRequired()) {
            authLauncher.launch(getAuthText(featureToAuth), fragmentActivity, onResult)
        } else {
            onResult(AuthResult.Success)
        }
    }

    private fun getAuthText(
        feature: Features,
    ): Int = when (feature) {
        Features.AUTOFILL_TO_USE_CREDENTIALS -> R.string.autofill_auth_text_for_using
        Features.AUTOFILL_TO_ACCESS_CREDENTIALS -> R.string.autofill_auth_text_for_access
    }
}
