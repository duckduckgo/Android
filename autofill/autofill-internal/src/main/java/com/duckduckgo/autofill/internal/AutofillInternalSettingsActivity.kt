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
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreenNoParams
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.internal.databinding.ActivityAutofillInternalSettingsBinding
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import java.text.SimpleDateFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

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

    @Inject
    lateinit var autofillStore: InternalAutofillStore

    private val dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM)

    @Inject
    lateinit var autofillFeature: AutofillFeature

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var neverSavedSiteRepository: NeverSavedSiteRepository

    @Inject
    lateinit var autofillJavascriptEnvironmentConfiguration: AutofillJavascriptEnvironmentConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureUiEventHandlers()
        refreshInstallationDaySettings()
        refreshDaysSinceInstall()
        refreshRemoteConfigSettings()
        refreshAutofillJsConfigSettings()
    }

    private fun refreshRemoteConfigSettings() {
        lifecycleScope.launch(dispatchers.io()) {
            val autofillEnabled = autofillFeature.self().isEnabled()
            val onByDefault = autofillFeature.onByDefault()
            val canSaveCredentials = autofillFeature.canSaveCredentials()
            val canInjectCredentials = autofillFeature.canInjectCredentials()
            val canGeneratePasswords = autofillFeature.canGeneratePasswords()
            val canAccessCredentialManagement = autofillFeature.canAccessCredentialManagement()

            withContext(dispatchers.main()) {
                binding.autofillTopLevelFeature.setSecondaryText(autofillEnabled.toString())
                binding.autofillOnByDefaultFeature.setSecondaryText("${onByDefault.isEnabled()} ${onByDefault.getRawStoredState()}")
                binding.canSaveCredentialsFeature.setSecondaryText(canSaveCredentials.isEnabled().toString())
                binding.canInjectCredentialsFeature.setSecondaryText(canInjectCredentials.isEnabled().toString())
                binding.canGeneratePasswordsFeature.setSecondaryText(canGeneratePasswords.isEnabled().toString())
                binding.canAccessCredentialManagementFeature.setSecondaryText(canAccessCredentialManagement.isEnabled().toString())
            }
        }
    }

    private fun refreshAutofillJsConfigSettings() {
        lifecycleScope.launch(dispatchers.io()) {
            val autofillJsConfigType = determineAutofillJsConfigType()
            val displayString = getString(R.string.autofillDevSettingsConfigDebugTitle, autofillJsConfigType)

            withContext(dispatchers.main()) {
                binding.changeAutofillJsConfigButton.setPrimaryText(displayString)
            }
        }
    }

    private fun determineAutofillJsConfigType(): String = autofillJavascriptEnvironmentConfiguration.getConfigType().toString()

    private fun configureUiEventHandlers() {
        configureEmailProtectionUiEventHandlers()
        configureLoginsUiEventHandlers()
        configureNeverSavedSitesEventHandlers()
        configureAutofillJsConfigEventHandlers()
    }

    private fun configureNeverSavedSitesEventHandlers() = with(binding) {
        numberNeverSavedSitesCount.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                neverSavedSiteRepository.clearNeverSaveList()
            }
        }
        addSampleNeverSavedSiteButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                // should only actually add one entry for all these attempts
                neverSavedSiteRepository.addToNeverSaveList("https://fill.dev")
                neverSavedSiteRepository.addToNeverSaveList("fill.dev")
                neverSavedSiteRepository.addToNeverSaveList("foo.fill.dev")
                neverSavedSiteRepository.addToNeverSaveList("fill.dev/?q=123")
            }
        }
    }

    private fun configureAutofillJsConfigEventHandlers() = with(binding) {
        val options = listOf(R.string.autofillDevSettingsConfigDebugOptionProduction, R.string.autofillDevSettingsConfigDebugOptionDebug)

        changeAutofillJsConfigButton.setClickListener {
            RadioListAlertDialogBuilder(this@AutofillInternalSettingsActivity)
                .setTitle(R.string.autofillDevSettingsConfigSectionTitle)
                .setOptions(options)
                .setPositiveButton(R.string.autofillDevSettingsOverrideMaxInstallDialogOkButtonText)
                .setNegativeButton(R.string.autofillDevSettingsOverrideMaxInstallDialogCancelButtonText)
                .addEventListener(
                    object : RadioListAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked(selectedItem: Int) {
                            lifecycleScope.launch(dispatchers.io()) {
                                when (selectedItem) {
                                    1 -> autofillJavascriptEnvironmentConfiguration.useProductionConfig()
                                    2 -> autofillJavascriptEnvironmentConfiguration.useDebugConfig()
                                }
                                refreshAutofillJsConfigSettings()
                            }
                        }
                    },
                )
                .show()
        }
    }

    private fun configureLoginsUiEventHandlers() {
        binding.addSampleLoginsButton.setClickListener {
            val timestamp = dateFormatter.format(System.currentTimeMillis())
            lifecycleScope.launch(dispatchers.io()) {
                sampleCredentials(domain = "fill.dev", username = "alice-$timestamp", password = "alice-$timestamp").save()
                sampleCredentials(domain = "fill.dev", username = "bob-$timestamp", password = "bob-$timestamp").save()
                sampleCredentials(domain = "subdomain1.fill.dev", username = "charlie-$timestamp", password = "charlie-$timestamp").save()
                sampleCredentials(domain = "subdomain2.fill.dev", username = "daniel-$timestamp", password = "daniel-$timestamp").save()
            }
        }

        binding.add100LoginsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                repeat(100) { sampleCredentials(domain = sampleUrlList.random(), username = "user-$it", password = "password-$it").save() }
            }
        }

        binding.add1000LoginsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                repeat(1_000) { sampleCredentials(domain = sampleUrlList.random(), username = "user-$it", password = "password-$it").save() }
            }
        }

        binding.addSampleLoginsContainingDuplicatesSameDomainButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                repeat(3) { sampleCredentials(domain = "fill.dev", username = "user").save() }
            }
        }

        binding.addSampleLoginsContainingDuplicatesAcrossSubdomainsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                repeat(3) { sampleCredentials("https://subdomain$it.fill.dev", username = "user").save() }
            }
        }

        binding.clearAllSavedLoginsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val count = autofillStore.getCredentialCount().first()
                withContext(dispatchers.main()) {
                    confirmLoginDeletion(count)
                }
            }
        }

        // keep the number of saved logins up-to-date
        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                autofillStore.getCredentialCount().collect { count ->
                    binding.clearAllSavedLoginsButton.isEnabled = count > 0
                    binding.clearAllSavedLoginsButton.setSecondaryText(getString(R.string.autofillDevSettingsClearLoginsSubtitle, count))
                }
            }
        }

        // keep the number of excluded sites (never saved) up-to-date
        lifecycleScope.launch(dispatchers.io()) {
            repeatOnLifecycle(STARTED) {
                neverSavedSiteRepository.neverSaveListCount().collect { count ->
                    binding.numberNeverSavedSitesCount.setSecondaryText(getString(R.string.autofillDevSettingsNeverSavedSitesCountSubtitle, count))
                }
            }
        }

        binding.viewSavedLoginsButton.setClickListener {
            globalActivityStarter.start(this, AutofillSettingsScreenNoParams)
        }
    }

    private fun confirmLoginDeletion(count: Int) {
        TextAlertDialogBuilder(this@AutofillInternalSettingsActivity)
            .setTitle(R.string.autofillDevSettingsClearLogins)
            .setMessage(getString(R.string.autofillDevSettingsClearLoginsConfirmationMessage, count))
            .setDestructiveButtons(true)
            .setPositiveButton(R.string.autofillDevSettingsClearLoginsDeleteButton)
            .setNegativeButton(R.string.autofillDevSettingsClearLoginsCancelButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onUserChoseToClearSavedLogins()
                    }
                },
            )
            .show()
    }

    private fun onUserChoseToClearSavedLogins() {
        lifecycleScope.launch(dispatchers.io()) {
            val idsToDelete = mutableListOf<Long>()
            autofillStore.getAllCredentials().first().forEach { login ->
                login.id?.let {
                    idsToDelete.add(it)
                }
            }

            logcat { "There are ${idsToDelete.size} logins to delete" }

            idsToDelete.forEach {
                autofillStore.deleteCredentials(it)
            }

            withContext(dispatchers.main()) {
                Toast.makeText(this@AutofillInternalSettingsActivity, "Deleted %d logins".format(idsToDelete.size), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureEmailProtectionUiEventHandlers() {
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

    private suspend fun LoginCredentials.save() {
        withContext(dispatchers.io()) {
            autofillStore.saveCredentials(this@save.domain ?: "", this@save)
        }
    }

    private fun sampleCredentials(
        domain: String = "fill.dev",
        username: String,
        password: String = "password-123",
    ): LoginCredentials {
        return LoginCredentials(username = username, password = password, domain = domain)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillInternalSettingsActivity::class.java)
        }

        private val sampleUrlList = listOf(
            "fill.dev",
            "duckduckgo.com",
            "spreadprivacy.com",
            "duck.com",
        )
    }
}
