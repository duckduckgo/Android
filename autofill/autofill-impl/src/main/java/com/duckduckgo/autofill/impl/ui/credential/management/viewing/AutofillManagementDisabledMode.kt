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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.ACTION_FINGERPRINT_ENROLL
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.provider.Settings.ACTION_SETTINGS
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementDisabledBinding
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.di.scopes.FragmentScope
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementDisabledMode : DuckDuckGoFragment() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private lateinit var binding: FragmentAutofillManagementDisabledBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAutofillManagementDisabledBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.disabledCta.setOnClickListener {
            launchDeviceAuthEnrollment()
        }
    }

    @SuppressLint("InlinedApi", "DEPRECATION")
    private fun launchDeviceAuthEnrollment() {
        logcat { "Launching device authentication enrollment. Manufacturer=[${appBuildConfig.manufacturer}],sdkInt=[${appBuildConfig.sdkInt}]" }

        when {
            appBuildConfig.manufacturer == "Xiaomi" -> {
                // Issue on Xiaomi: https://stackoverflow.com/questions/68484485/intent-action-fingerprint-enroll-on-redmi-results-in-exception
                Intent(SYSTEM_SETTINGS_ACTION).safeLaunchSettingsActivity(tryFallback = false)
            }

            appBuildConfig.sdkInt >= Build.VERSION_CODES.R -> {
                val intent = Intent(ACTION_BIOMETRIC_ENROLL)
                if (appBuildConfig.manufacturer.equals("TCL", ignoreCase = true)) {
                    // https://app.asana.com/1/137249556945/project/1200930669568058/task/1210133000985922?focus=true
                    intent.putExtra(EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, DEVICE_CREDENTIAL)
                }
                intent.safeLaunchSettingsActivity(tryFallback = true)
            }

            appBuildConfig.sdkInt >= Build.VERSION_CODES.P -> {
                Intent(ACTION_FINGERPRINT_ENROLL).safeLaunchSettingsActivity(tryFallback = true)
            }

            else -> {
                Intent(ACTION_SECURITY_SETTINGS).safeLaunchSettingsActivity(tryFallback = true)
            }
        }

        requireActivity().finish()
    }

    /**
     * Attempt to launch the given activity.
     * If it fails because the activity wasn't found, try launching the main settings activity if tryFallback=true.
     */
    private fun Intent.safeLaunchSettingsActivity(tryFallback: Boolean) {
        try {
            requireActivity().startActivity(this)
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "${e.asLog()}. Trying fallback? $tryFallback" }
            if (tryFallback) {
                Intent(SYSTEM_SETTINGS_ACTION).safeLaunchSettingsActivity(tryFallback = false)
            }
        }
    }

    companion object {
        fun instance() = AutofillManagementDisabledMode()
        private const val SYSTEM_SETTINGS_ACTION = ACTION_SETTINGS
    }
}
