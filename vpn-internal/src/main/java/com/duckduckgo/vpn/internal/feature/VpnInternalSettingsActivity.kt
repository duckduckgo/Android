/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.vpn.internal.databinding.ActivityVpnInternalSettingsBinding
import com.duckduckgo.vpn.internal.feature.rules.ExceptionRulesDebugActivity
import com.duckduckgo.vpn.internal.feature.transparency.TransparencyModeDebugReceiver

class VpnInternalSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityVpnInternalSettingsBinding by viewBinding()
    private var receiver: TransparencyModeDebugReceiver? = null

    private val transparencyToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        if (toggleState) {
            TransparencyModeDebugReceiver.turnOnIntent()
        } else {
            TransparencyModeDebugReceiver.turnOffIntent()
        }.also { sendBroadcast(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupTransparencyMode()
        setupAppTrackerExceptionRules()
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver?.let { it.unregister() }
    }

    private fun setupAppTrackerExceptionRules() {
        binding.exceptionRules.setOnClickListener {
            startActivity(ExceptionRulesDebugActivity.intent(this))
        }
    }

    private fun setupTransparencyMode() {

        // we use the same receiver as it makes IPC much easier
        receiver = TransparencyModeDebugReceiver(this) {
            // avoid duplicating broadcast intent when toggle changes state
            binding.transparencyModeToggle.setOnCheckedChangeListener(null)
            if (TransparencyModeDebugReceiver.isTurnOnIntent(it)) {
                binding.transparencyModeToggle.isChecked = true
            } else if (TransparencyModeDebugReceiver.isTurnOffIntent(it)) {
                binding.transparencyModeToggle.isChecked = false
            }
            binding.transparencyModeToggle.setOnCheckedChangeListener(transparencyToggleListener)
        }.apply { register() }

        binding.transparencyModeToggle.setOnCheckedChangeListener(transparencyToggleListener)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnInternalSettingsActivity::class.java)
        }
    }
}
