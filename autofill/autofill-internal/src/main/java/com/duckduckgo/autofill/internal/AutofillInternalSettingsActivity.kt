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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.InternalDevSettings
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementRepository
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePassword.AutofillImportViaGooglePasswordManagerScreen
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Companion.RESULT_KEY_DETAILS
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Error
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Success
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.UserCancelled
import com.duckduckgo.autofill.impl.reporting.AutofillSiteBreakageReportingDataStore
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.ui.credential.management.survey.AutofillSurveyStore
import com.duckduckgo.autofill.internal.databinding.ActivityAutofillInternalSettingsBinding
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.launchAutofillProviderSystemSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
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

    @Inject
    lateinit var credentialImporter: CredentialImporter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var autofillPrefsStore: AutofillPrefsStore

    private val dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM)

    @Inject
    lateinit var autofillFeature: AutofillFeature

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var neverSavedSiteRepository: NeverSavedSiteRepository

    @Inject
    lateinit var autofillJavascriptEnvironmentConfiguration: AutofillJavascriptEnvironmentConfiguration

    @Inject
    lateinit var autofillSurveyStore: AutofillSurveyStore

    @Inject
    lateinit var engagementRepository: AutofillEngagementRepository

    @Inject
    lateinit var reportBreakageDataStore: AutofillSiteBreakageReportingDataStore

    @Inject
    lateinit var csvCredentialConverter: CsvCredentialConverter

    @Inject
    lateinit var autofillImportPasswordConfigStore: AutofillImportPasswordConfigStore

    @Inject
    lateinit var webViewCapabilityChecker: WebViewCapabilityChecker

    private var passwordImportWatcher = ConflatedJob()

    // used to output duration of import
    private var importStartTime: Long = 0

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val fileUrl = data?.data

            logcat { "onActivityResult for CSV file request. resultCode=${result.resultCode}. uri=$fileUrl" }
            if (fileUrl != null) {
                lifecycleScope.launch(dispatchers.io()) {
                    when (val parseResult = csvCredentialConverter.readCsv(fileUrl)) {
                        is CsvCredentialImportResult.Success -> {
                            importStartTime = System.currentTimeMillis()

                            credentialImporter.import(
                                parseResult.loginCredentialsToImport,
                                parseResult.numberCredentialsInSource,
                            )
                            observePasswordInputUpdates()
                        }

                        is CsvCredentialImportResult.Error -> {
                            FAILED_IMPORT_GENERIC_ERROR.showSnackbar()
                        }
                    }
                }
            }
        }
    }

    private val importGooglePasswordsFlowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        logcat { "onActivityResult for Google Password Manager import flow. resultCode=${result.resultCode}" }

        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                when (IntentCompat.getParcelableExtra(it, RESULT_KEY_DETAILS, ImportGooglePasswordResult::class.java)) {
                    is Success -> observePasswordInputUpdates()
                    is Error -> FAILED_IMPORT_GENERIC_ERROR.showSnackbar()
                    is UserCancelled, null -> {
                    }
                }
            }
        }
    }

    private fun observePasswordInputUpdates() {
        passwordImportWatcher += lifecycleScope.launch {
            credentialImporter.getImportStatus().collect {
                when (it) {
                    is InProgress -> {
                        logcat { "import status: $it" }
                    }

                    is Finished -> {
                        passwordImportWatcher.cancel()
                        val duration = System.currentTimeMillis() - importStartTime
                        logcat { "Imported ${it.savedCredentials} passwords, skipped ${it.numberSkipped}. Took ${duration}ms" }
                        "Imported ${it.savedCredentials} passwords".showSnackbar()
                    }
                }
            }
        }
    }

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
            val autofillEnabled = autofillFeature.self()
            val onByDefault = autofillFeature.onByDefault()
            val onForExistingUsers = autofillFeature.onForExistingUsers()
            val canIntegrateAutofill = autofillFeature.canIntegrateAutofillInWebView()
            val canSaveCredentials = autofillFeature.canSaveCredentials()
            val canInjectCredentials = autofillFeature.canInjectCredentials()
            val canGeneratePasswords = autofillFeature.canGeneratePasswords()
            val canAccessCredentialManagement = autofillFeature.canAccessCredentialManagement()
            val canCategorizeUnknownUsername = autofillFeature.canCategorizeUnknownUsername()

            withContext(dispatchers.main()) {
                binding.autofillTopLevelFeature.setSecondaryText(autofillEnabled.description())
                binding.autofillOnByDefaultFeature.setSecondaryText(onByDefault.description())
                binding.autofillOnForExistingUsersFeature.setSecondaryText(onForExistingUsers.description())
                binding.canIntegrateAutofillWithWebView.setSecondaryText(canIntegrateAutofill.description())
                binding.canSaveCredentialsFeature.setSecondaryText(canSaveCredentials.description())
                binding.canInjectCredentialsFeature.setSecondaryText(canInjectCredentials.description())
                binding.canGeneratePasswordsFeature.setSecondaryText(canGeneratePasswords.description())
                binding.canAccessCredentialManagementFeature.setSecondaryText(canAccessCredentialManagement.description())
                binding.autofillTopLevelFeature.setSecondaryText(canCategorizeUnknownUsername.description())
            }
        }
    }

    private fun Toggle.description(includeRawState: Boolean = false): String {
        return if (includeRawState) {
            "${isEnabled()} ${getRawStoredState()}"
        } else {
            isEnabled().toString()
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
        configureSurveyEventHandlers()
        configureEngagementEventHandlers()
        configureReportBreakagesHandlers()
        configureDeclineCounterHandlers()
        configureImportPasswordsEventHandlers()
    }

    private fun configureReportBreakagesHandlers() {
        binding.reportBreakageClearButton.setOnClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                reportBreakageDataStore.clearAllReports()
            }
            Toast.makeText(this@AutofillInternalSettingsActivity, R.string.autofillDevSettingsReportBreakageHistoryCleared, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun configureImportPasswordsEventHandlers() {
        binding.importPasswordsLaunchGooglePasswordWebpage.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val googlePasswordsUrl = autofillImportPasswordConfigStore.getConfig().launchUrlGooglePasswords
                startActivity(browserNav.openInNewTab(this@AutofillInternalSettingsActivity, googlePasswordsUrl))
            }
        }
        binding.importPasswordsLaunchGooglePasswordCustomFlow.setClickListener {
            lifecycleScope.launch {
                val webViewWebMessageSupport = webViewCapabilityChecker.isSupported(WebMessageListener)
                val webViewDocumentStartJavascript = webViewCapabilityChecker.isSupported(DocumentStartJavaScript)
                if (webViewDocumentStartJavascript && webViewWebMessageSupport) {
                    val intent =
                        globalActivityStarter.startIntent(this@AutofillInternalSettingsActivity, AutofillImportViaGooglePasswordManagerScreen)
                    importGooglePasswordsFlowLauncher.launch(intent)
                } else {
                    Toast.makeText(this@AutofillInternalSettingsActivity, "WebView version not supported", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.importPasswordsImportCsv.setClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importCsvLauncher.launch(intent)
        }

        binding.importPasswordsResetImportedFlagButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                autofillStore.hasEverImportedPasswords = false
                autofillStore.hasDeclinedPasswordManagementImportPromo = false
                autofillStore.hasDeclinedInBrowserPasswordImportPromo = false
                autofillStore.inBrowserImportPromoShownCount = 0
            }
            Toast.makeText(
                this@AutofillInternalSettingsActivity,
                getString(R.string.autofillDevSettingsResetGooglePasswordsImportFlagConfirmation),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun configureEngagementEventHandlers() {
        binding.engagementClearEngagementHistoryButton.setOnClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                engagementRepository.clearData(preserveToday = false)
                withContext(dispatchers.main()) {
                    val message = getString(R.string.autofillDevSettingsEngagementHistoryCleared)
                    Toast.makeText(this@AutofillInternalSettingsActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun configureSurveyEventHandlers() {
        binding.autofillSurveyResetButton.setOnClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                autofillSurveyStore.resetPreviousSurveys()
            }
            Toast.makeText(this, getString(R.string.autofillDevSettingsSurveySectionResetted), Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureDeclineCounterHandlers() {
        binding.autofillDeclineCounterResetButton.setOnClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                autofillPrefsStore.resetAllValues()
            }
            Toast.makeText(this, getString(R.string.autofillDevSettingsDeclineCounterResetted), Toast.LENGTH_SHORT).show()
        }
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
        binding.accessAutofillSystemSettingsButton.setOnClickListener {
            this.launchAutofillProviderSystemSettings()
        }

        binding.addSampleLoginsButton.setClickListener {
            val timestamp = dateFormatter.format(System.currentTimeMillis())
            lifecycleScope.launch(dispatchers.io()) {
                listOf(
                    sampleCredentials(domain = "fill.dev", username = "alice-$timestamp", password = "alice-$timestamp"),
                    sampleCredentials(domain = "fill.dev", username = "bob-$timestamp", password = "bob-$timestamp"),
                    sampleCredentials(domain = "subdomain1.fill.dev", username = "charlie-$timestamp", password = "charlie-$timestamp"),
                    sampleCredentials(domain = "subdomain2.fill.dev", username = "daniel-$timestamp", password = "daniel-$timestamp"),
                ).save()
            }
        }

        binding.add100LoginsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val credentials = mutableListOf<LoginCredentials>()
                repeat(100) {
                    credentials.add(sampleCredentials(domain = sampleUrlList.random(), username = "user-$it", password = "password-$it"))
                }
                credentials.save()
            }
        }

        binding.add1000LoginsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val credentials = mutableListOf<LoginCredentials>()
                repeat(1_000) {
                    credentials.add(sampleCredentials(domain = sampleUrlList.random(), username = "user-$it", password = "password-$it"))
                }
                credentials.save()
            }
        }

        binding.addSampleLoginsContainingDuplicatesSameDomainButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val credentials = mutableListOf<LoginCredentials>()
                repeat(3) { credentials.add(sampleCredentials(domain = "fill.dev", username = "user")) }
                credentials.save()
            }
        }

        binding.addSampleLoginsContainingDuplicatesAcrossSubdomainsButton.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val credentials = mutableListOf<LoginCredentials>()
                repeat(3) { credentials.add(sampleCredentials("https://subdomain$it.fill.dev", username = "user")) }
                credentials.save()
            }
        }

        binding.addMixedCaseUsernameDuplicates.setClickListener {
            lifecycleScope.launch(dispatchers.io()) {
                val credentials = mutableListOf<LoginCredentials>()
                credentials.add(sampleCredentials("https://autofill.me", username = "username"))
                credentials.add(sampleCredentials("https://autofill.me", username = "UseRNamE"))
                credentials.add(sampleCredentials("https://autofill.me", username = "USERNAME"))
                credentials.save()
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
        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(STARTED) {
                neverSavedSiteRepository.neverSaveListCount().collect { count ->
                    binding.numberNeverSavedSitesCount.setSecondaryText(getString(R.string.autofillDevSettingsNeverSavedSitesCountSubtitle, count))
                }
            }
        }

        binding.viewSavedLoginsButton.setClickListener {
            globalActivityStarter.start(this, AutofillPasswordsManagementScreen(source = InternalDevSettings))
        }
    }

    private fun confirmLoginDeletion(count: Int) {
        TextAlertDialogBuilder(this@AutofillInternalSettingsActivity)
            .setTitle(R.string.autofillDevSettingsClearLogins)
            .setMessage(getString(R.string.autofillDevSettingsClearLoginsConfirmationMessage, count))
            .setPositiveButton(R.string.autofillDevSettingsClearLoginsDeleteButton, DESTRUCTIVE)
            .setNegativeButton(R.string.autofillDevSettingsClearLoginsCancelButton, GHOST_ALT)
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
            autofillStore.getCredentialCount()
            val deleted = autofillStore.deleteAllCredentials().size
            withContext(dispatchers.main()) {
                Toast.makeText(this@AutofillInternalSettingsActivity, "Deleted %d logins".format(deleted), Toast.LENGTH_SHORT).show()
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

    private fun String.showSnackbar(duration: Int = Snackbar.LENGTH_LONG) {
        Snackbar.make(binding.root, this, duration).show()
    }

    private fun Context.daysInstalledOverrideOptions(): List<Pair<String, Int>> {
        return listOf(
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionNever), -1),
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionNumberDays, 21), 21),
            Pair(getString(R.string.autofillDevSettingsOverrideMaxInstalledOptionAlways), Int.MAX_VALUE),
        )
    }

    private suspend fun List<LoginCredentials>.save() {
        withContext(dispatchers.io()) {
            autofillStore.bulkInsert(this@save)
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

        private const val FAILED_IMPORT_GENERIC_ERROR = "Failed to import passwords due to an error"

        private val sampleUrlList = listOf(
            "fill.dev",
            "duckduckgo.com",
            "spreadprivacy.com",
            "duck.com",
        )
    }
}
