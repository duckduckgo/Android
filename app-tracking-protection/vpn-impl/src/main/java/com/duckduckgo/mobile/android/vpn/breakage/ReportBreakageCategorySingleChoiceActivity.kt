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

package com.duckduckgo.mobile.android.vpn.breakage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategorySingleChoiceViewModel.Command
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategorySingleChoiceViewModel.ViewState
import com.duckduckgo.mobile.android.vpn.databinding.ActivityReportBreakageCategorySingleChoiceBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import dagger.WrongScope
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@WrongScope(
    comment = "To use the right scope we first need to enable dagger component nesting",
    correctScope = ActivityScope::class,
)
@InjectWith(VpnScope::class)
class ReportBreakageCategorySingleChoiceActivity : DuckDuckGoActivity() {

    @Inject lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject lateinit var metadataReporter: ReportBreakageMetadataReporter

    private val binding: ActivityReportBreakageCategorySingleChoiceBinding by viewBinding()
    private val viewModel: ReportBreakageCategorySingleChoiceViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private lateinit var brokenApp: BrokenApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The value should never be "unknown" we just do this because getParcelableExtra returns
        // nullable
        brokenApp = intent.getParcelableExtra(APP_PACKAGE_ID_EXTRA) ?: BrokenApp("unknown", "unknown")

        setContentView(binding.root)
        configureListeners()
        configureObservers()
        setupToolbar(toolbar)
        setupViews()
    }

    override fun onStart() {
        super.onStart()
        deviceShieldPixels.didShowReportBreakageSingleChoiceForm()
    }

    private fun setupViews() {
        binding.appBreakageFormDisclaimer.text =
            HtmlCompat.fromHtml(
                getString(R.string.atp_ReportBreakageFormDisclaimerText),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
    }

    private fun configureListeners() {
        val categories = viewModel.shuffledCategories.map { it.category }
        binding.categoriesSelection.onAction {
            RadioListAlertDialogBuilder(this)
                .setTitle(getString(R.string.atp_ReportBreakageCategoriesTitle))
                .setOptions(categories, viewModel.indexSelected + 1)
                .setPositiveButton(android.R.string.ok)
                .setNegativeButton(android.R.string.cancel)
                .addEventListener(
                    object : RadioListAlertDialogBuilder.EventListener() {
                        override fun onRadioItemSelected(selectedItem: Int) {
                            viewModel.onCategoryIndexChanged(selectedItem - 1)
                        }

                        override fun onPositiveButtonClicked(selectedItem: Int) {
                            viewModel.onCategoryAccepted()
                        }

                        override fun onNegativeButtonClicked() {
                            viewModel.onCategorySelectionCancelled()
                        }
                    },
                )
                .show()
        }
        binding.ctaNextFormSubmit.setOnClickListener { viewModel.onSubmitPressed() }
    }

    private fun configureObservers() {
        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { processCommand(it) }
        }
        lifecycleScope.launch {
            viewModel.viewState()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { render(it) }
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        lifecycleScope.launch {
            val issue =
                IssueReport(
                    appName = brokenApp.appName,
                    appPackageId = brokenApp.appPackageId,
                    description = binding.appBreakageFormFeedbackInput.text.toString(),
                    category = viewModel.viewState.value.categorySelected.toString(),
                    customMetadata =
                    Base64.encodeToString(
                        metadataReporter.getVpnStateMetadata(brokenApp.appPackageId).toByteArray(),
                        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
                    ),
                )
            deviceShieldPixels.sendAppBreakageReport(issue.toMap())
            setResult(RESULT_OK, Intent().apply { issue.addToIntent(this) })
            finish()
        }
    }

    private fun render(viewState: ViewState) {
        val category =
            viewState.categorySelected?.let { getString(viewState.categorySelected.category) }.orEmpty()
        binding.categoriesSelection.text = category
        binding.ctaNextFormSubmit.isEnabled = viewState.submitAllowed
    }

    companion object {

        private const val APP_PACKAGE_ID_EXTRA = "APP_PACKAGE_ID_EXTRA"

        fun intent(context: Context, brokenApp: BrokenApp): Intent {
            return Intent(context, ReportBreakageCategorySingleChoiceActivity::class.java).apply {
                putExtra(APP_PACKAGE_ID_EXTRA, brokenApp)
            }
        }
    }
}
