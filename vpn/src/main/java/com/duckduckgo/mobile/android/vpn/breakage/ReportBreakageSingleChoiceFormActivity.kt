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

package com.duckduckgo.mobile.android.vpn.breakage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityReportBreakageTextSingleChoiceBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReportBreakageSingleChoiceFormActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private val binding: ActivityReportBreakageTextSingleChoiceBinding by viewBinding()
    private val viewModel: ReportBreakageSingleChoiceFormViewModel by bindViewModel()

    private val adapter = ReportBreakageSingleChoiceFormAdapter(object : ReportBreakageSingleChoiceFormAdapter.Listener {
        override fun onChoiceSelected(
            choice: Choice,
            position: Int
        ) {
            viewModel.onChoiceSelected(choice)
        }
    })

    private val toolbar
        get() = binding.includeToolbar.defaultToolbar

    private var brokenApp: BrokenApp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        brokenApp = intent.getParcelableExtra(APP_PACKAGE_ID_EXTRA)

        setContentView(binding.root)
        setupToolbar(toolbar)
        setupViews()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        deviceShieldPixels.didShowReportBreakageSingleChoiceForm()
    }

    fun setupViews() {
        binding.appBreakageFormDisclaimer.text =
            HtmlCompat.fromHtml(getString(R.string.atp_ReportBreakageFormDisclaimerText), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.reportBreakageChoicesRecycler.adapter = adapter
        binding.ctaNextFormSubmit.setOnClickListener {
            viewModel.onSubmitChoices()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getChoices()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: ReportBreakageSingleChoiceFormView.Command) {
        when (command) {
            is ReportBreakageSingleChoiceFormView.Command.SubmitChoice -> submitChoice(command.selectedChoice)
        }
    }

    private fun submitChoice(choice: Choice) {
        val intent = Intent().apply {
            IssueReport(
                appName = brokenApp?.appName,
                appPackageId = brokenApp?.appPackageId,
                loginInfo = getString(choice.questionStringRes)
            ).addToIntent(this)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun renderViewState(state: ReportBreakageSingleChoiceFormView.State) {
        adapter.update(state.choices)
        binding.ctaNextFormSubmit.isEnabled = state.canSubmit
    }

    companion object {
        private const val APP_PACKAGE_ID_EXTRA = "APP_PACKAGE_ID_EXTRA"

        fun intent(
            context: Context,
            brokenApp: BrokenApp
        ): Intent {
            return Intent(context, ReportBreakageSingleChoiceFormActivity::class.java).apply {
                putExtra(APP_PACKAGE_ID_EXTRA, brokenApp)
            }
        }
    }
}
