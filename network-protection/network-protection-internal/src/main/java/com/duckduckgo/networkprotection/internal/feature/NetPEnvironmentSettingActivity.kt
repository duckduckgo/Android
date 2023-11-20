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

package com.duckduckgo.networkprotection.internal.feature

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.webkit.URLUtil
import android.widget.CompoundButton
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.internal.databinding.ActivityNetpEnvInternalSettingsBinding
import com.duckduckgo.networkprotection.internal.feature.NetPEnvironmentSettingActivity.Companion.NetPEnvironmentSettingScreen
import com.duckduckgo.networkprotection.internal.network.NetPInternalEnvDataStore
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPEnvironmentSettingScreen::class)
class NetPEnvironmentSettingActivity : DuckDuckGoActivity() {

    @Inject lateinit var store: NetPInternalEnvDataStore

    private val binding: ActivityNetpEnvInternalSettingsBinding by viewBinding()

    private val endpointToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        store.useNetpCustomEnvironmentUrl = toggleState
        binding.urlInput.isEditable = toggleState
        if (!toggleState) binding.validation.hide()
        runValidation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        binding.urlInput.text = store.netpCustomEnvironmentUrl ?: ""
        binding.urlInput.isEditable = store.useNetpCustomEnvironmentUrl
        binding.endpointToggle.setIsChecked(store.useNetpCustomEnvironmentUrl)
        binding.endpointToggle.setOnCheckedChangeListener(endpointToggleListener)
        binding.urlInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // NOOP
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // NOOP
                }

                override fun afterTextChanged(p0: Editable?) {
                    store.netpCustomEnvironmentUrl = p0.toString()
                    runValidation()
                }
            },
        )
        runValidation()
    }

    private fun runValidation() {
        if (canUrlBeChanged()) {
            binding.validation.hide()
        } else {
            binding.validation.show()
            binding.validation.text = "URL is not valid or toggle is not enabled and the default URL will be used"
        }
    }

    private fun canUrlBeChanged(): Boolean {
        val storedUrl = store.netpCustomEnvironmentUrl
        val isCustomSettingEnabled = store.useNetpCustomEnvironmentUrl
        return isCustomSettingEnabled && !storedUrl.isNullOrEmpty() && URLUtil.isValidUrl(storedUrl)
    }

    companion object {
        internal object NetPEnvironmentSettingScreen : ActivityParams
    }
}
