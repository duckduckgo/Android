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

package com.duckduckgo.app.dev.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityDevSettingsBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command
import com.duckduckgo.app.dev.settings.privacy.TrackerDataDevReceiver.Companion.DOWNLOAD_TDS_INTENT_ACTION
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DevSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityDevSettingsBinding by viewBinding()

    private val viewModel: DevSettingsViewModel by bindViewModel()

    private val nextTdsToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onNextTdsToggled(isChecked)
    }

    private val startupTraceToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onStartupTraceToggled(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                viewState.let {
                    binding.nextTdsEnabled.quietlySetIsChecked(it.nextTdsEnabled, nextTdsToggleListener)
                    binding.enableAppStartupTrace.quietlySetIsChecked(it.startupTraceEnabled, startupTraceToggleListener)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.SendTdsIntent -> sendTdsIntent()
            else -> TODO()
        }
    }

    private fun goToUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    private fun sendTdsIntent() {
        Toast.makeText(this, getString(R.string.devSettingsScreenTdsWait), Toast.LENGTH_SHORT).show()
        val intent = Intent()
        intent.action = DOWNLOAD_TDS_INTENT_ACTION
        sendBroadcast(intent)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DevSettingsActivity::class.java)
        }
    }
}
