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

package com.duckduckgo.sync.impl.auth

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.BuildConfig
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

interface DeviceAuthenticator {
    /**
     * This method can be used to check if the user's device has a valid device authentication enrolled (Fingerprint, PIN, pattern or password).
     */
    fun hasValidDeviceAuthentication(): Boolean

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
     * Returns true if the user has to authenticate to use sync. This is always true in production.
     *
     * When running some specific UI tests, this can be set to false with a build flag to allow us to have increased test coverage.
     */
    fun isAuthenticationRequired(): Boolean {
        return BuildConfig.AUTH_REQUIRED
    }

    /**
     * Launches the device authentication enrollment screen from system settings.
     */
    fun launchDeviceAuthEnrollment(context: Context)

    sealed class AuthResult {
        data object Success : AuthResult()
        data object UserCancelled : AuthResult()
        data class Error(val reason: String) : AuthResult()
    }

    data class AuthConfiguration(
        val requireUserAction: Boolean = false,
        val displayTextResource: Int = R.string.sync_auth_text_for_access,
        val displayTitleResource: Int = R.string.sync_biometric_prompt_title,
    )
}

@ContributesBinding(AppScope::class)
class RealDeviceAuthenticator @Inject constructor(
    private val deviceAuthChecker: SupportedDeviceAuthChecker,
    private val appBuildConfig: AppBuildConfig,
    private val authLauncher: AuthLauncher,
    private val autofillAuthGracePeriod: DeviceAuthorizationGracePeriod,
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
        fragmentActivity: FragmentActivity,
        config: AuthConfiguration,
        onResult: (AuthResult) -> Unit,
    ) {
        if (config.requireUserAction || autofillAuthGracePeriod.isAuthRequired()) {
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

    @SuppressLint("InlinedApi", "DEPRECATION")
    override fun launchDeviceAuthEnrollment(context: Context) {
        when {
            appBuildConfig.manufacturer == "Xiaomi" -> {
                // Issue on Xiaomi: https://stackoverflow.com/questions/68484485/intent-action-fingerprint-enroll-on-redmi-results-in-exception
                SYSTEM_SETTINGS_ACTION.safeLaunchSettingsActivity(context, tryFallback = false)
            }

            appBuildConfig.sdkInt >= Build.VERSION_CODES.R -> {
                Settings.ACTION_BIOMETRIC_ENROLL.safeLaunchSettingsActivity(context, tryFallback = true)
            }

            appBuildConfig.sdkInt >= Build.VERSION_CODES.P -> {
                Settings.ACTION_FINGERPRINT_ENROLL.safeLaunchSettingsActivity(context, tryFallback = true)
            }

            else -> {
                Settings.ACTION_SECURITY_SETTINGS.safeLaunchSettingsActivity(context, tryFallback = true)
            }
        }
    }

    /**
     * Attempt to launch the given activity.
     * If it fails because the activity wasn't found, try launching the main settings activity if tryFallback=true.
     */
    private fun String.safeLaunchSettingsActivity(context: Context, tryFallback: Boolean) {
        try {
            context.startActivity(Intent(this))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "${e.asLog()}. Trying fallback? $tryFallback" }
            if (tryFallback) {
                SYSTEM_SETTINGS_ACTION.safeLaunchSettingsActivity(context, tryFallback = false)
            }
        }
    }

    companion object {
        private const val SYSTEM_SETTINGS_ACTION = Settings.ACTION_SETTINGS
    }
}
