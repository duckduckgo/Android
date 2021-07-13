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

import android.app.Activity
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
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.android.synthetic.internal.activity_dev_settings.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class DevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: DevSettingsViewModel by bindViewModel()

    private val nextTdsToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onNextTdsToggled(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_settings)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun configureUiEventHandlers() {
        privacyTest1.setOnClickListener { viewModel.goToPrivacyTest1() }
        privacyTest2.setOnClickListener { viewModel.goToPrivacyTest2() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                viewState.let {
                    nextTdsEnabled.quietlySetIsChecked(it.nextTdsEnabled, nextTdsToggleListener)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.UpdateTheme -> TODO()
            is Command.SendTdsIntent -> sendTdsIntent()
            is Command.GoToUrl -> goToUrl(it.url)
            null -> TODO()
        }
    }

    private fun goToUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    private fun sendTdsIntent() {
        Timber.d("MARCOS send intent")
        Toast.makeText(this, "Please wait while we download the tds version", Toast.LENGTH_SHORT).show()
        val intent = Intent()
        intent.action = "downloadTds"
        sendBroadcast(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FEEDBACK_REQUEST_CODE) {
            handleFeedbackResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleFeedbackResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, R.string.thanksForTheFeedback, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val FEEDBACK_REQUEST_CODE = 100

        fun intent(context: Context): Intent {
            return Intent(context, DevSettingsActivity::class.java)
        }
    }
}
