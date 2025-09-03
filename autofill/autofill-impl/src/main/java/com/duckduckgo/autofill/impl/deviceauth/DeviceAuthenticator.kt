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
import com.duckduckgo.autofill.impl.BuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DeviceAuthenticator {
    /**
     * This method can be used to check if the user's device has a valid device authentication enrolled (Fingerprint, PIN, pattern or password).
     */
    fun hasValidDeviceAuthentication(): Boolean

    /**
     * Launches a device authentication flow from a [fragment]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        fragment: Fragment,
        config: AuthConfiguration = AuthConfiguration(),
        onResult: (AuthResult) -> Unit,
    )

    /**
     * Launches a device authentication flow from a [fragmentActivity]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        fragmentActivity: FragmentActivity,
        config: AuthConfiguration = AuthConfiguration(),
        onResult: (AuthResult) -> Unit,
    )

    /**
     * Returns true if the user has to authenticate to use autofill. This is always true in production.
     *
     * When running some specific UI tests, this can be set to false with a build flag to allow us to have increased test coverage.
     */
    fun isAuthenticationRequiredForAutofill(): Boolean {
        return BuildConfig.AUTH_REQUIRED
    }

    sealed class AuthResult {
        data object Success : AuthResult()
        data object UserCancelled : AuthResult()
        data class Error(val reason: String) : AuthResult()
    }

    data class AuthConfiguration(
        val requireUserAction: Boolean = false,
        val displayTextResource: Int = R.string.autofill_auth_text_for_access,
        val displayTitleResource: Int = R.string.biometric_prompt_title,
    )
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
        fragment: Fragment,
        config: AuthConfiguration,
        onResult: (AuthResult) -> Unit,
    ) {
        if (isAuthenticationRequiredForAutofill() && (config.requireUserAction || autofillAuthGracePeriod.isAuthRequired())) {
            authLauncher.launch(
                featureTitleText = config.displayTitleResource,
                featureAuthText = config.displayTextResource,
                fragment = fragment,
                onResult = onResult,
            )
        } else {
            onResult(AuthResult.Success)
        }
    }

    @UiThread
    override fun authenticate(
        fragmentActivity: FragmentActivity,
        config: AuthConfiguration,
        onResult: (AuthResult) -> Unit,
    ) {
        if (isAuthenticationRequiredForAutofill() && (config.requireUserAction || autofillAuthGracePeriod.isAuthRequired())) {
            authLauncher.launch(
                featureTitleText = config.displayTitleResource,
                featureAuthText = config.displayTextResource,
                fragmentActivity = fragmentActivity,
                onResult = onResult,
            )
        } else {
            onResult(AuthResult.Success)
        }
    }
}
