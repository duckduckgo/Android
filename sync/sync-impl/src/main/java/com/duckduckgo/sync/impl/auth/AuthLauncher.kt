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
@file:Suppress("ktlint:standard:filename")

package com.duckduckgo.sync.impl.auth

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.UserCancelled
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface AuthLauncher {

    fun launch(
        @StringRes featureTitleText: Int,
        @StringRes featureAuthText: Int,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    )
}

@ContributesBinding(AppScope::class)
class RealAuthLauncher @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val deviceAuthorizationGracePeriod: DeviceAuthorizationGracePeriod,
) : AuthLauncher {

    override fun launch(
        @StringRes featureTitleText: Int,
        @StringRes featureAuthText: Int,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            fragmentActivity,
            ContextCompat.getMainExecutor(context),
            getCallBack(onResult),
        )

        prompt.authenticate(getPromptInfo(titleText = featureTitleText, featureAuthText = featureAuthText))
    }

    private fun getCallBack(
        onResult: (AuthResult) -> Unit,
    ): BiometricPrompt.AuthenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(
            errorCode: Int,
            errString: CharSequence,
        ) {
            super.onAuthenticationError(errorCode, errString)
            logcat { "onAuthenticationError: ($errorCode) $errString" }

            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                onResult(UserCancelled)
            } else {
                onResult(Error(String.format("(%d) %s", errorCode, errString)))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            logcat { "onAuthenticationSucceeded ${result.authenticationType}" }
            deviceAuthorizationGracePeriod.recordSuccessfulAuthorization()
            onResult(Success)
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            logcat(VERBOSE) { "onAuthenticationFailed" }
        }
    }

    private fun getPromptInfo(titleText: Int, featureAuthText: Int): BiometricPrompt.PromptInfo {
        val biometricPromptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
        biometricPromptInfoBuilder.setTitle(context.getString(titleText))

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
