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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementDisabledBinding
import com.duckduckgo.di.scopes.FragmentScope
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
        when {
            appBuildConfig.manufacturer == "Xiaomi" ->
                // Issue on Xiaomi: https://stackoverflow.com/questions/68484485/intent-action-fingerprint-enroll-on-redmi-results-in-exception
                requireActivity().startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            appBuildConfig.sdkInt >= Build.VERSION_CODES.R ->
                requireActivity().startActivity(Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL))
            appBuildConfig.sdkInt >= Build.VERSION_CODES.P ->
                requireActivity().startActivity(Intent(android.provider.Settings.ACTION_FINGERPRINT_ENROLL))
            else ->
                requireActivity().startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        }

        requireActivity().finish()
    }

    companion object {
        fun instance() = AutofillManagementDisabledMode()
    }
}
