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

package com.duckduckgo.app.browser.autofill

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.DoNotUseEmailProtection
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.UsePersonalEmailAddress
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.UsePrivateAliasAddress
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.api.store.AutofillStore.ContainsCredentialsResult
import com.duckduckgo.autofill.api.store.AutofillStore.ContainsCredentialsResult.ExactMatch
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL_TO_USE_CREDENTIALS
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AutofillCredentialsSelectionResultHandler @Inject constructor(
    private val deviceAuthenticator: DeviceAuthenticator,
    private val declineCounter: AutofillDeclineCounter,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autofillStore: AutofillStore,
    private val pixel: Pixel,
    private val autofillDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val appBuildConfig: AppBuildConfig,
) {

    suspend fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: Fragment,
        credentialInjector: CredentialInjector,
    ) {
        val originalUrl = result.getString(CredentialAutofillPickerDialog.KEY_URL) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            Timber.v("Autofill: User cancelled credential selection")
            credentialInjector.returnNoCredentialsWithPage(originalUrl)
            return
        }

        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialAutofillPickerDialog.KEY_CREDENTIALS) ?: return

        pixel.fire(AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN)

        withContext(dispatchers.main()) {
            deviceAuthenticator.authenticate(AUTOFILL_TO_USE_CREDENTIALS, browserTabFragment) {
                when (it) {
                    Success -> {
                        Timber.v("Autofill: user selected credential to use, and successfully authenticated")
                        pixel.fire(AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL)
                        credentialInjector.shareCredentialsWithPage(originalUrl, selectedCredentials)
                    }

                    UserCancelled -> {
                        Timber.d("Autofill: user selected credential to use, but cancelled without authenticating")
                        pixel.fire(AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED)
                        credentialInjector.returnNoCredentialsWithPage(originalUrl)
                    }

                    is Error -> {
                        Timber.w("Autofill: user selected credential to use, but there was an error when authenticating: ${it.reason}")
                        pixel.fire(AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE)
                        credentialInjector.returnNoCredentialsWithPage(originalUrl)
                    }
                }
            }
        }
    }

    suspend fun processPromptToDisableAutofill(
        context: Context,
        viewModel: BrowserTabViewModel,
    ) {
        autofillDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN)
        withContext(dispatchers.main()) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.autofillDisableAutofillPromptTitle))
                .setMessage(context.getString(R.string.autofillDisableAutofillPromptMessage))
                .setPositiveButton(context.getString(R.string.autofillDisableAutofillPromptPositiveButton)) { _, _ -> onKeepUsingAutofill() }
                .setNegativeButton(context.getString(R.string.autofillDisableAutofillPromptNegativeButton)) { _, _ -> onDisableAutofill(viewModel) }
                .setOnCancelListener { onCancelledPromptToDisableAutofill() }
                .show()
        }
    }

    private fun onCancelledPromptToDisableAutofill() {
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED)
    }

    private fun onKeepUsingAutofill() {
        Timber.i("User selected to keep using autofill; will not prompt to disable again")
        appCoroutineScope.launch {
            declineCounter.disableDeclineCounter()
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING)
    }

    private fun onDisableAutofill(viewModel: BrowserTabViewModel) {
        appCoroutineScope.launch {
            autofillStore.autofillEnabled = false
            declineCounter.disableDeclineCounter()
            refreshCurrentWebPageToDisableAutofill(viewModel)
            Timber.i("Autofill disabled at user request")
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE)
    }

    suspend fun processSaveCredentialsResult(
        result: Bundle,
        credentialSaver: AutofillCredentialSaver,
    ): LoginCredentials? {
        autofillDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS) ?: return null
        val originalUrl = result.getString(CredentialSavePickerDialog.KEY_URL) ?: return null
        val savedCredentials = credentialSaver.saveCredentials(originalUrl, selectedCredentials)
        if (savedCredentials != null) {
            declineCounter.disableDeclineCounter()
        }
        return savedCredentials
    }

    suspend fun processUpdateCredentialsResult(
        result: Bundle,
        credentialSaver: AutofillCredentialSaver,
    ): LoginCredentials? {
        autofillDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS) ?: return null
        val originalUrl = result.getString(CredentialUpdateExistingCredentialsDialog.KEY_URL) ?: return null
        val updateType =
            result.getParcelable<CredentialUpdateType>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE) ?: return null
        return credentialSaver.updateCredentials(originalUrl, selectedCredentials, updateType)
    }

    suspend fun processGeneratePasswordResult(
        result: Bundle,
        viewModel: BrowserTabViewModel,
        tabId: String,
    ) {
        val originalUrl = result.getString(UseGeneratedPasswordDialog.KEY_URL) ?: return
        if (result.getBoolean(UseGeneratedPasswordDialog.KEY_ACCEPTED)) {
            onUserAcceptedToUseGeneratedPassword(result, tabId, originalUrl, viewModel)
        } else {
            viewModel.rejectGeneratedPassword(originalUrl)
        }
    }

    suspend fun processPrivateDuckAddressInjectedEvent(
        duckAddress: String,
        tabId: String,
        originalUrl: String,
        autoSaveLogin: Boolean,
    ) {
        if (!autoSaveLogin) {
            return
        }

        // this could be triggered from email autofill, which might happen even if saving passwords is disabled so need to guard here
        if (!autofillCapabilityChecker.canSaveCredentialsFromWebView(originalUrl)) {
            return
        }

        val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
        if (autologinId == null) {
            saveDuckAddressForCurrentSite(duckAddress = duckAddress, tabId = tabId, url = originalUrl)
        } else {
            val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
            if (existingAutoSavedLogin == null) {
                Timber.w("Can't find saved login with autosavedLoginId: $autologinId")
                saveDuckAddressForCurrentSite(duckAddress = duckAddress, tabId = tabId, url = originalUrl)
            } else {
                updateUsernameIfDifferent(existingAutoSavedLogin, duckAddress)
            }
        }
    }

    private suspend fun onUserAcceptedToUseGeneratedPassword(
        result: Bundle,
        tabId: String,
        originalUrl: String,
        viewModel: BrowserTabViewModel,
    ) {
        val username = result.getString(UseGeneratedPasswordDialog.KEY_USERNAME)
        val password = result.getString(UseGeneratedPasswordDialog.KEY_PASSWORD) ?: return
        val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
        val matchType = existingCredentialMatchDetector.determine(originalUrl, username, password)
        Timber.v("autoSavedLoginId: %s. Match type against existing entries: %s", autologinId, matchType.javaClass.simpleName)

        if (autologinId == null) {
            saveLoginIfNotAlreadySaved(matchType, originalUrl, username, password, tabId)
        } else {
            val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
            if (existingAutoSavedLogin == null) {
                Timber.w("Can't find saved login with autosavedLoginId: $autologinId")
                saveLoginIfNotAlreadySaved(matchType, originalUrl, username, password, tabId)
            } else {
                updateLoginIfDifferent(existingAutoSavedLogin, username, password)
            }
        }
        viewModel.acceptGeneratedPassword(originalUrl)
    }

    private suspend fun updateLoginIfDifferent(
        autosavedLogin: LoginCredentials,
        username: String?,
        password: String,
    ) {
        if (username == autosavedLogin.username && password == autosavedLogin.password) {
            Timber.i("Generated password (and username) matches existing login; nothing to do here")
        } else {
            Timber.i("Updating existing login with new username and/or password. Login id is: %s", autosavedLogin.id)
            autofillStore.updateCredentials(autosavedLogin.copy(username = username, password = password))
        }
    }

    private suspend fun updateUsernameIfDifferent(
        autosavedLogin: LoginCredentials,
        username: String,
    ) {
        if (username == autosavedLogin.username) {
            Timber.i("Generated username matches existing login; nothing to do here")
        } else {
            Timber.i("Updating existing login with new username. Login id is: %s", autosavedLogin.id)
            autofillStore.updateCredentials(autosavedLogin.copy(username = username))
        }
    }

    private suspend fun saveLoginIfNotAlreadySaved(
        matchType: ContainsCredentialsResult,
        originalUrl: String,
        username: String?,
        password: String,
        tabId: String,
    ) {
        when (matchType) {
            ExactMatch -> Timber.v("Already got an exact match; nothing to do here")
            else -> {
                autofillStore.saveCredentials(
                    originalUrl,
                    LoginCredentials(domain = originalUrl, username = username, password = password),
                )?.id?.let { savedId ->
                    Timber.i("New login saved because no exact matches were found, with ID: $savedId")
                    autoSavedLoginsMonitor.setAutoSavedLoginId(savedId, tabId)
                }
            }
        }
    }

    private suspend fun saveDuckAddressForCurrentSite(
        duckAddress: String,
        tabId: String,
        url: String,
    ) {
        val credentials = LoginCredentials(domain = url, username = duckAddress, password = null)
        autofillStore.saveCredentials(rawUrl = url, credentials = credentials)?.id?.let { savedId ->
            Timber.i("New login saved for duck address %s on site %s because no exact matches were found, with ID: %s", duckAddress, url, savedId)
            autoSavedLoginsMonitor.setAutoSavedLoginId(savedId, tabId)
        }
    }

    private suspend fun refreshCurrentWebPageToDisableAutofill(viewModel: BrowserTabViewModel) {
        withContext(dispatchers.main()) {
            viewModel.onRefreshRequested()
        }
    }

    fun processSaveOrUpdatePromptDismissed() {
        autofillDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)
    }

    fun processSaveOrUpdatePromptShown() {
        autofillDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = true)
    }

    @SuppressLint("NewApi")
    fun processEmailProtectionSelectEmailChoice(
        result: Bundle,
        viewModel: BrowserTabViewModel,
    ) {
        val userSelection: UseEmailResultType = if (appBuildConfig.sdkInt >= VERSION_CODES.TIRAMISU) {
            result.getParcelable(EmailProtectionChooserDialog.KEY_RESULT, UseEmailResultType::class.java)!!
        } else {
            result.getParcelable(EmailProtectionChooserDialog.KEY_RESULT)!!
        }
        val originalUrl = result.getString(EmailProtectionChooserDialog.KEY_URL)!!

        when (userSelection) {
            UsePersonalEmailAddress -> viewModel.useAddress(originalUrl)
            UsePrivateAliasAddress -> viewModel.consumeAlias(originalUrl)
            DoNotUseEmailProtection -> viewModel.cancelAutofillTooltip()
        }
    }

    interface CredentialInjector {
        fun shareCredentialsWithPage(
            originalUrl: String,
            credentials: LoginCredentials,
        )

        fun returnNoCredentialsWithPage(originalUrl: String)
    }

    interface AutofillCredentialSaver {
        suspend fun saveCredentials(
            url: String,
            credentials: LoginCredentials,
        ): LoginCredentials?

        suspend fun updateCredentials(
            url: String,
            credentials: LoginCredentials,
            updateType: CredentialUpdateType,
        ): LoginCredentials?
    }
}
