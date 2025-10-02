/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.settings

import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.text.toSpanned
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.AutofillSettings
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordActivityParams
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementDeviceUnsupportedMode
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.AutofillManagementDisabledMode
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.AskToConfirmResetExcludedSites
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.ImportPasswordsFromGoogle
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.NavigatePasswordList
import com.duckduckgo.autofill.impl.ui.settings.AutofillSettingsViewModel.Command.NavigateToHowToSyncWithDesktop
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AutofillSettingsScreen::class)
class AutofillSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    private val globalAutofillToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@OnCheckedChangeListener
            if (isChecked) {
                viewModel.onEnableAutofill(
                    getAutofillSettingsLaunchSource(),
                )
            } else {
                viewModel.onDisableAutofill(
                    getAutofillSettingsLaunchSource(),
                )
            }
        }

    private fun getAutofillSettingsLaunchSource(): AutofillScreenLaunchSource? =
        intent.getActivityParams(AutofillSettingsScreen::class.java)?.let {
            return it.source
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setTitle(R.string.autofillSettingsActivityTitle)
        observeViewModel()
        configureViewListeners()
        configureInfoText()
        sendLaunchPixel(savedInstanceState)
    }

    private fun sendLaunchPixel(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            viewModel.sendLaunchPixel(extractLaunchSource())
        }
    }

    private fun extractLaunchSource(): AutofillScreenLaunchSource {
        intent.getActivityParams(AutofillSettingsScreen::class.java)?.let {
            return it.source
        }

        // default if nothing else matches
        return AutofillScreenLaunchSource.Unknown
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.viewState(extractLaunchSource())
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { state ->
                    val isAutofillAvailable = state.autofillUnsupported.not() && state.autofillDisabled.not()
                    binding.viewSwitcher.displayedChild = if (isAutofillAvailable) 0 else 1
                    if (isAutofillAvailable) {
                        binding.autofillAvailable.autofillEnabledToggle.quietlySetIsChecked(
                            state.autofillEnabled,
                            globalAutofillToggleListener,
                        )
                        binding.autofillAvailable.passwordsListItem.setSecondaryText(state.loginsCount.toString())
                        binding.autofillAvailable.excludedSitesSection.isVisible = state.canResetExcludedSites
                        binding.autofillAvailable.importPasswordsOption.isVisible = state.canImportFromGooglePasswords
                    } else {
                        if (state.autofillUnsupported) {
                            showDeviceUnsupportedMode()
                        } else if (state.autofillDisabled) {
                            showDisabledMode()
                        }
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.commands.collectLatest {
                when (it) {
                    is NavigatePasswordList -> {
                        globalActivityStarter.start(
                            this@AutofillSettingsActivity,
                            AutofillPasswordsManagementScreen(source = AutofillSettings),
                        )
                    }

                    ImportPasswordsFromGoogle -> {
                        launchImportPasswordsScreen()
                    }

                    NavigateToHowToSyncWithDesktop -> {
                        launchImportPasswordsFromDesktopSyncScreen()
                    }

                    AskToConfirmResetExcludedSites -> {
                        askToConfirmResetExcludedSites()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.checkDeviceRequirements()
    }

    private fun configureViewListeners() {
        binding.autofillAvailable.autofillEnabledToggle.setOnCheckedChangeListener(globalAutofillToggleListener)
        binding.autofillAvailable.passwordsListItem.setOnClickListener {
            viewModel.onPasswordListClicked()
        }
        binding.autofillAvailable.importPasswordsOption.setOnClickListener {
            viewModel.onImportPasswordsClicked(AutofillImportLaunchSource.AutofillSettings)
        }
        binding.autofillAvailable.syncDesktopPasswordsOption.setOnClickListener {
            viewModel.onImportFromDesktopWithSyncClicked(getAutofillSettingsLaunchSource())
        }
        binding.autofillAvailable.excludedSitesOption.setOnClickListener {
            viewModel.onResetExcludedSitesClicked(getAutofillSettingsLaunchSource())
        }
    }

    private fun showDeviceUnsupportedMode() {
        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(TAG_UNSUPPORTED)?.let { remove(it) }
            replace(binding.autofillUnsupported.fragmentContainerView.id, AutofillManagementDeviceUnsupportedMode.instance(), TAG_UNSUPPORTED)
        }
    }

    private fun showDisabledMode() {
        supportFragmentManager.commit {
            supportFragmentManager.findFragmentByTag(TAG_DISABLED)?.let { remove(it) }

            replace(binding.autofillUnsupported.fragmentContainerView.id, AutofillManagementDisabledMode.instance(), TAG_DISABLED)
        }
    }

    private fun configureInfoText() {
        binding.autofillAvailable.autofillInfoText.addClickableLink(
            annotation = "learn_more_link",
            textSequence = binding.root.context.prependIconToText(
                R.string.autofillSettingsAutofillSubtitle,
                R.drawable.ic_lock_solid_12,
            ).toSpanned(),
            onClick = {
                launchHelpPage()
            },
        )
    }

    private fun launchHelpPage() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = LEARN_MORE_LINK,
                screenTitle = getString(R.string.credentialManagementAutofillHelpPageTitle),
            ),
        )
    }

    private fun launchImportPasswordsScreen() {
        val dialog = ImportFromGooglePasswordsDialog.instance(AutofillImportLaunchSource.AutofillSettings)
        dialog.show(supportFragmentManager, IMPORT_FROM_GPM_DIALOG_TAG)
    }

    private fun launchImportPasswordsFromDesktopSyncScreen() {
        globalActivityStarter.start(this, ImportPasswordActivityParams)
    }

    private fun askToConfirmResetExcludedSites() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.autofillSettingsClearNeverForThisSiteDialogTitle)
            .setMessage(R.string.autofillSettingsInstructionNeverForThisSite)
            .setPositiveButton(R.string.autofillSettingsClearNeverForThisSiteDialogPositiveButton, DESTRUCTIVE)
            .setNegativeButton(R.string.autofillSettingsClearNeverForThisSiteDialogNegativeButton, GHOST_ALT)
            .setCancellable(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onResetExcludedSitesConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onResetExcludedSitesCancelled()
                    }

                    override fun onDialogCancelled() {
                        viewModel.onResetExcludedSitesCancelled()
                    }
                },
            )
            .show()
    }

    companion object {
        private const val LEARN_MORE_LINK =
            "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/password-manager-security/"
        private const val IMPORT_FROM_GPM_DIALOG_TAG = "IMPORT_FROM_GPM_DIALOG_TAG"
        private const val TAG_DISABLED = "tag_fragment_disabled"
        private const val TAG_UNSUPPORTED = "tag_fragment_unsupported"
    }
}
