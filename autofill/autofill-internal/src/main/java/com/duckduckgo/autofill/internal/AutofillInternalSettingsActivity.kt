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

package com.duckduckgo.autofill.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.autofill.internal.databinding.ActivityAutofillInternalSettingsBinding
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@InjectWith(ActivityScope::class)
class AutofillInternalSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAutofillInternalSettingsBinding by viewBinding()

    @Inject
    lateinit var inContextDataStore: EmailProtectionInContextDataStore

    @Inject
    lateinit var userBrowserProperties: UserBrowserProperties

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureUiEventHandlers()
        refreshInstallationDaySettings()
        refreshDaysSinceInstall()
    }

    private fun configureUiEventHandlers() {
        binding.emailProtectionClearNeverAskAgainButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                inContextDataStore.resetNeverAskAgainChoice()
                getString(R.string.autofillDevSettingsEmailProtectionNeverAskAgainChoiceCleared).showToast()
            }
        }

        binding.emailProtectionSignOutButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                emailManager.signOut()
                getString(R.string.autofillDevSettingsEmailProtectionSignedOut).showToast()
            }
        }

        @Suppress("DEPRECATION")
        binding.configureDaysFromInstallValue.setClickListener {
            RadioListAlertDialogBuilder(this)
                .setTitle(R.string.autofillDevSettingsOverrideMaxInstallDialogTitle)
                .setOptions(daysInstalledOverrideOptions().map { it.first })
                .setPositiveButton(R.string.autofillDevSettingsOverrideMaxInstallDialogOkButtonText)
                .setNegativeButton(R.string.autofillDevSettingsOverrideMaxInstallDialogCancelButtonText)
                .addEventListener(
                    object : RadioListAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked(selectedItem: Int) {
                            val daysInstalledOverrideChosen = daysInstalledOverrideOptions()[selectedItem - 1].second

                            lifecycleScope.launch(dispatchers.io()) {
                                inContextDataStore.updateMaximumPermittedDaysSinceInstallation(daysInstalledOverrideChosen)
                                refreshInstallationDaySettings()
                            }
                        }
                    },
                )
                .show()
        }

        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                emailManager.signedInFlow().collect() { signedIn ->
                    binding.emailProtectionSignOutButton.isEnabled = signedIn

                    val text = if (signedIn) {
                        getString(R.string.autofillDevSettingsEmailProtectionSignedInAs, emailManager.getEmailAddress())
                    } else {
                        getString(R.string.autofillDevSettingsEmailProtectionNotSignedIn)
                    }

                    binding.emailProtectionSignOutButton.setSecondaryText(text)
                }
            }
        }
    }

    private fun refreshInstallationDaySettings() {
        lifecycleScope.launch {
            val installDays = inContextDataStore.getMaximumPermittedDaysSinceInstallation()

            withContext(dispatchers.main()) {
                val formatted = when {
                    (installDays < 0) -> getString(R.string.autofillDevSettingsOverrideMaxInstalledDaysNeverShow)
                    (installDays == Int.MAX_VALUE) -> getString(R.string.autofillDevSettingsOverrideMaxInstalledDaysAlwaysShow)
                    else -> getString(R.string.autofillDevSettingsOverrideMaxInstalledDaysSetting, installDays)
                }
                binding.configureDaysFromInstallValue.setPrimaryText(formatted)
            }
        }
    }

    private fun refreshDaysSinceInstall() {
        lifecycleScope.launch(dispatchers.io()) {
            val formatted = getString(R.string.autofillDevSettingsDaysSinceInstall, userBrowserProperties.daysSinceInstalled())

            withContext(dispatchers.main()) {
                binding.emailProtectionDaysSinceInstallValue.setPrimaryText(formatted)
            }
        }
    }

    private suspend fun String.showToast() {
        withContext(dispatchers.main()) {
            Toast.makeText(this@AutofillInternalSettingsActivity, this@showToast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun Context.daysInstalledOverrideOptions(): List<Pair<String, Int>> {
        return listOf(
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionNever), -1),
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionNumberDays, 21), 21),
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionAlways), Int.MAX_VALUE),
        )
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillInternalSettingsActivity::class.java)
        }
    }
}
