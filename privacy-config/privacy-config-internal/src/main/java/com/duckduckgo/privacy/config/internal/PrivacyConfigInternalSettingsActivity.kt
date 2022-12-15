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

package com.duckduckgo.privacy.config.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.webkit.URLUtil
import android.widget.CompoundButton
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.privacy.config.internal.databinding.ActivityPrivacyConfigInternalSettingsBinding
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PrivacyConfigInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var store: DevPrivacyConfigSettingsDataStore

    private val binding: ActivityPrivacyConfigInternalSettingsBinding by viewBinding()

    private val endpointToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        store.useCustomPrivacyConfigUrl = toggleState
        binding.urlInput.isEditable = toggleState
        if (!toggleState) binding.validation.hide()
        runValidation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        binding.urlInput.text = store.remotePrivacyConfigUrl ?: ""
        binding.urlInput.isEditable = store.useCustomPrivacyConfigUrl
        binding.endpointToggle.setIsChecked(store.useCustomPrivacyConfigUrl)
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
                    store.remotePrivacyConfigUrl = p0.toString()
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
        val storedUrl = store.remotePrivacyConfigUrl
        val isCustomSettingEnabled = store.useCustomPrivacyConfigUrl
        return isCustomSettingEnabled && !storedUrl.isNullOrEmpty() && URLUtil.isValidUrl(storedUrl)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, PrivacyConfigInternalSettingsActivity::class.java)
        }
    }
}
