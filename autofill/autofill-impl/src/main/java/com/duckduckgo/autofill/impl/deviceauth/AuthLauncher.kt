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

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface AuthLauncher {
    fun launch(
        @StringRes featureAuthText: Int,
        fragment: Fragment,
        onResult: (AuthResult) -> Unit,
    )

    fun launch(
        @StringRes featureAuthText: Int,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    )
}

@ContributesBinding(AppScope::class)
class RealAuthLauncher @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val autofillAuthorizationGracePeriod: AutofillAuthorizationGracePeriod,
) : AuthLauncher {
    private val biometricPromptInfoBuilder by lazy {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
    }

    override fun launch(
        @StringRes featureAuthText: Int,
        fragment: Fragment,
        onResult: (AuthResult) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            fragment,
            ContextCompat.getMainExecutor(context),
            getCallBack(onResult),
        )

        prompt.authenticate(getPromptInfo(featureAuthText))
    }

    override fun launch(
        @StringRes featureAuthText: Int,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            fragmentActivity,
            ContextCompat.getMainExecutor(context),
            getCallBack(onResult),
        )

        prompt.authenticate(getPromptInfo(featureAuthText))
    }

    private fun getCallBack(
        onResult: (AuthResult) -> Unit,
    ): BiometricPrompt.AuthenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(
            errorCode: Int,
            errString: CharSequence,
        ) {
            super.onAuthenticationError(errorCode, errString)
            Timber.d("onAuthenticationError: (%d) %s", errorCode, errString)

            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                onResult(UserCancelled)
            } else {
                onResult(Error(String.format("(%d) %s", errorCode, errString)))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Timber.d("onAuthenticationSucceeded ${result.authenticationType}")
            autofillAuthorizationGracePeriod.recordSuccessfulAuthorization()
            onResult(Success)
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Timber.v("onAuthenticationFailed")
        }
    }

    private fun getPromptInfo(featureAuthText: Int): BiometricPrompt.PromptInfo {
        // https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt.PromptInfo.Builder#setallowedauthenticators
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 28-29. Setting an unsupported value on an affected Android version will result in an error when calling build().
        return if (appBuildConfig.sdkInt != Build.VERSION_CODES.Q && appBuildConfig.sdkInt != Build.VERSION_CODES.P) {
            biometricPromptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            biometricPromptInfoBuilder.setDeviceCredentialAllowed(true)
        }.run {
            this.setSubtitle(context.getString(featureAuthText)).build()
        }
    }
}
