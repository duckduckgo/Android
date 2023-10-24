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

package com.duckduckgo.autofill.impl.deviceauth

import android.os.Build
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.Features
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DeviceAuthenticator {
    /**
     * This method can be used to check if the user's device has a valid device authentication enrolled (Fingerprint, PIN, pattern or password).
     */
    fun hasValidDeviceAuthentication(): Boolean

    /**
     * Launches a device authentication flow for a specific [featureToAuth] from a [fragment]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        featureToAuth: Features,
        fragment: Fragment,
        onResult: (AuthResult) -> Unit,
    )

    /**
     * Launches a device authentication flow for a specific [featureToAuth] from a [fragmentActivity]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        featureToAuth: Features,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    )

    sealed class AuthResult {
        object Success : AuthResult()
        object UserCancelled : AuthResult()
        data class Error(val reason: String) : AuthResult()
    }

    enum class Features {
        AUTOFILL_TO_USE_CREDENTIALS,
        AUTOFILL_TO_ACCESS_CREDENTIALS,
    }
}

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
