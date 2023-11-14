/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.brokensite

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.ViewState
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityBrokenSiteBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.mobile.android.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import java.util.*
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class BrokenSiteActivity : DuckDuckGoActivity() {

    private val binding: ActivityBrokenSiteBinding by viewBinding()
    private val viewModel: BrokenSiteViewModel by bindViewModel()

    @Inject lateinit var webViewVersionProvider: WebViewVersionProvider

    @Inject lateinit var appBuildConfig: AppBuildConfig

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val brokenSites
        get() = binding.contentBrokenSites

    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureListeners()
        configureObservers()
        setupToolbar(toolbar)
        if (savedInstanceState == null) {
            consumeIntentExtra()
        }
    }

    private fun consumeIntentExtra() {
        val url = intent.getStringExtra(URL_EXTRA).orEmpty()
        val blockedTrackers = intent.getStringExtra(BLOCKED_TRACKERS_EXTRA).orEmpty()
        val upgradedHttps = intent.getBooleanExtra(UPGRADED_TO_HTTPS_EXTRA, false)
        val surrogates = intent.getStringExtra(SURROGATES_EXTRA).orEmpty()
        val urlParametersRemoved = intent.getBooleanExtra(URL_PARAMETERS_REMOVED_EXTRA, false)
        val consentManaged = intent.getBooleanExtra(CONSENT_MANAGED_EXTRA, false)
        val consentOptOutFailed = intent.getBooleanExtra(CONSENT_OPT_OUT_FAILED_EXTRA, false)
        val consentSelfTestFailed = intent.getBooleanExtra(CONSENT_SELF_TEST_FAILED_EXTRA, false)
        val params = intent.getStringArrayExtra(BOOLEAN_PARAMS).orEmpty()
        val errorCodes = intent.getStringArrayExtra(ERROR_CODES).orEmpty()
        val httpErrorCodes = intent.getStringExtra(HTTP_ERROR_CODES).orEmpty()
        val isDesktopMode = intent.getBooleanExtra(IS_DESKTOP_MODE, false)
        viewModel.setInitialBrokenSite(
            url = url,
            blockedTrackers = blockedTrackers,
            surrogates = surrogates,
            upgradedHttps = upgradedHttps,
            urlParametersRemoved = urlParametersRemoved,
            consentManaged = consentManaged,
            consentOptOutFailed = consentOptOutFailed,
            consentSelfTestFailed = consentSelfTestFailed,
            params = params,
            errorCodes = errorCodes,
            httpErrorCodes = httpErrorCodes,
            isDesktopMode = isDesktopMode,
        )
    }

    private fun configureListeners() {
        val categories = viewModel.shuffledCategories.map { getString(it.category) }.toTypedArray()

        brokenSites.categoriesSelection.onAction {
            RadioListAlertDialogBuilder(this)
                .setTitle(getString(R.string.brokenSitesCategoriesTitle))
                .setOptions(categories.toList(), viewModel.indexSelected + 1)
                .setPositiveButton(android.R.string.yes)
                .setNegativeButton(android.R.string.no)
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
                .build()
                .dismissOnDestroy(lifecycleOwner = this)
                .show()
        }
        brokenSites.submitButton.setOnClickListener {
            if (!submitted) {
                val webViewVersion = webViewVersionProvider.getFullVersion()
                val description = brokenSites.brokenSiteFormFeedbackInput.text
                val loginSite = brokenSites.brokenSiteFormLoginInput.text
                viewModel.onSubmitPressed(webViewVersion, description, loginSite)
                submitted = true
            }
        }

        brokenSites.brokenSiteFormLoginInput.addFocusChangedListener { _, hasFocus ->
            if (hasFocus) {
                brokenSites.brokenSiteFormLoginInput.hint = getString(R.string.brokenSitesLoginSmallHint)
            } else {
                brokenSites.brokenSiteFormLoginInput.hint = getString(R.string.brokenSitesLoginHint)
            }
        }

        brokenSites.brokenSiteFormLoginInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // NOOP
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // NOOP
                }

                override fun afterTextChanged(p0: Editable?) {
                    runValidation()
                }
            },
        )

        brokenSites.protectionsToggle.setOnProtectionsToggledListener(viewModel::onProtectionsToggled)
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            it?.let { processCommand(it) }
        }
        viewModel.viewState.observe(this) {
            it?.let { render(it) }
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        val snackbar = Snackbar.make(binding.root, getString(R.string.brokenSiteSubmitted), Snackbar.LENGTH_SHORT)
        snackbar.addCallback(
            object : Snackbar.Callback() {
                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int,
                ) {
                    finish()
                }
            },
        )
        snackbar.show()
    }

    private fun render(viewState: ViewState) {
        val category = viewState.categorySelected?.let {
            getString(viewState.categorySelected.category)
        }.orEmpty()
        brokenSites.categoriesSelection.text = category
        brokenSites.submitButton.isEnabled = viewState.submitAllowed

        if (appBuildConfig.deviceLocale.language == Locale.ENGLISH.language) {
            if (viewState.categorySelected?.key == BrokenSiteCategory.LOGIN_CATEGORY_KEY) {
                brokenSites.brokenSiteFormLoginInput.show()
                brokenSites.submitButton.isEnabled = false
                runValidation()
            } else {
                brokenSites.brokenSiteFormLoginInput.gone()
                brokenSites.submitButton.isEnabled = true
            }
        }

        if (viewState.protectionsState != null) {
            brokenSites.protectionsToggle.isVisible = true
            brokenSites.protectionsToggle.setState(viewState.protectionsState)
        } else {
            brokenSites.protectionsToggle.isVisible = false
        }
    }

    private fun runValidation() {
        if (brokenSites.brokenSiteFormLoginInput.isVisible) {
            brokenSites.submitButton.isEnabled = brokenSites.brokenSiteFormLoginInput.text.isNotEmpty()
        } else {
            brokenSites.submitButton.isEnabled = true
        }
    }

    companion object {

        private const val URL_EXTRA = "URL_EXTRA"
        private const val BLOCKED_TRACKERS_EXTRA = "BLOCKED_TRACKERS_EXTRA"
        private const val UPGRADED_TO_HTTPS_EXTRA = "UPGRADED_TO_HTTPS_EXTRA"
        private const val SURROGATES_EXTRA = "SURROGATES_EXTRA"
        private const val URL_PARAMETERS_REMOVED_EXTRA = "URL_PARAMETERS_REMOVED_EXTRA"
        private const val CONSENT_MANAGED_EXTRA = "CONSENT_MANAGED_EXTRA"
        private const val CONSENT_OPT_OUT_FAILED_EXTRA = "CONSENT_OPT_OUT_FAILED_EXTRA"
        private const val CONSENT_SELF_TEST_FAILED_EXTRA = "CONSENT_SELF_TEST_FAILED_EXTRA"
        private const val BOOLEAN_PARAMS = "BOOLEAN_PARAMS"
        private const val ERROR_CODES = "ERROR_CODES"
        private const val HTTP_ERROR_CODES = "HTTP_ERROR_CODES"
        private const val IS_DESKTOP_MODE = "IS_DESKTOP_MODE"

        fun intent(
            context: Context,
            data: BrokenSiteData,
        ): Intent {
            val intent = Intent(context, BrokenSiteActivity::class.java)
            intent.putExtra(URL_EXTRA, data.url)
            intent.putExtra(BLOCKED_TRACKERS_EXTRA, data.blockedTrackers)
            intent.putExtra(SURROGATES_EXTRA, data.surrogates)
            intent.putExtra(UPGRADED_TO_HTTPS_EXTRA, data.upgradedToHttps)
            intent.putExtra(URL_PARAMETERS_REMOVED_EXTRA, data.urlParametersRemoved)
            intent.putExtra(CONSENT_MANAGED_EXTRA, data.consentManaged)
            intent.putExtra(CONSENT_OPT_OUT_FAILED_EXTRA, data.consentOptOutFailed)
            intent.putExtra(CONSENT_SELF_TEST_FAILED_EXTRA, data.consentSelfTestFailed)
            intent.putExtra(BOOLEAN_PARAMS, data.params.toTypedArray())
            intent.putExtra(ERROR_CODES, data.errorCodes.toTypedArray())
            intent.putExtra(HTTP_ERROR_CODES, data.httpErrorCodes)
            intent.putExtra(IS_DESKTOP_MODE, data.isDesktopMode)
            return intent
        }
    }
}

private fun DaxAlertDialog.dismissOnDestroy(lifecycleOwner: LifecycleOwner): DaxAlertDialog {
    lifecycleOwner.lifecycle.addObserver(
        @SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dismiss()
            }
        },
    )
    return this
}
