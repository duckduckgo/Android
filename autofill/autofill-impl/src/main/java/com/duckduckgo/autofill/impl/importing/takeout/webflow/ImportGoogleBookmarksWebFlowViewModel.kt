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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor
import com.duckduckgo.autofill.impl.importing.takeout.store.BookmarkImportConfigStore
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step.JavascriptStep
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.ExitFlowAsFailure
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.PromptUserToConfirmFlowCancellation
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.HideWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.TakeoutActionError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.TakeoutActionSuccess
import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.UnknownMessageFormat
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebAutomationError
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.autofill.impl.store.ReauthenticationHandler
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ImportGoogleBookmarksWebFlowViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val reauthenticationHandler: ReauthenticationHandler,
    private val autofillFeature: AutofillFeature,
    private val bookmarkImportProcessor: BookmarkImportProcessor,
    private val bookmarkImportConfigStore: BookmarkImportConfigStore,
    private val takeoutWebMessageParser: TakeoutWebMessageParser,
    private val webFlowStepObserver: BookmarkImportWebFlowStepObserver,
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Initializing)
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = MutableSharedFlow<Command>(replay = 0, extraBufferCapacity = 1)
    val commands: SharedFlow<Command> = _commands

    suspend fun loadInitialWebpage() {
        webFlowStepObserver.startFlow()
        withContext(dispatchers.io()) {
            val initialUrl = bookmarkImportConfigStore.getConfig().launchUrlGoogleTakeout
            _viewState.value = ViewState.LoadingWebPage(initialUrl)
        }
    }

    fun onDownloadDetected(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String,
        folderName: String,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            logcat { "Download detected: $url, mimeType: $mimeType, contentDisposition: $contentDisposition" }
            webFlowStepObserver.updateStep(Step.DownloadDetected)

            // Check if this looks like a valid Google Takeout bookmark export
            val isValidImport = isTakeoutZipDownloadLink(mimeType, url, contentDisposition)

            if (isValidImport) {
                logcat { "Valid bookmark import detected, starting download... $url" }
                processBookmarkImport(url, userAgent, folderName)
            } else {
                logcat { "Invalid import type detected, exiting as failure. URL: $url" }
                _commands.emit(ExitFlowAsFailure(UserCannotImportReason.DownloadError))
            }
        }
    }

    private fun isTakeoutZipDownloadLink(
        mimeType: String,
        url: String,
        contentDisposition: String?,
    ): Boolean =
        mimeType == "application/zip" ||
            mimeType == "application/octet-stream" ||
            url.contains(".zip") ||
            url.contains("takeout.google.com") ||
            contentDisposition?.contains("attachment") == true

    private suspend fun processBookmarkImport(
        url: String,
        userAgent: String,
        folderName: String,
    ) {
        val importResult = bookmarkImportProcessor.downloadAndImportFromTakeoutZipUrl(url, userAgent, folderName)
        handleImportResult(importResult, folderName)
    }

    private suspend fun handleImportResult(
        importResult: BookmarkImportProcessor.ImportResult,
        folderName: String,
    ) {
        webFlowStepObserver.updateStep(Step.ImportFinished(importResult))

        when (importResult) {
            is BookmarkImportProcessor.ImportResult.Success -> {
                logcat { "Successfully imported ${importResult.importedCount} bookmarks into '$folderName' folder" }
                _commands.emit(Command.ExitFlowWithSuccess(importResult.importedCount))
            }

            is BookmarkImportProcessor.ImportResult.Error.DownloadError -> {
                _commands.emit(ExitFlowAsFailure(UserCannotImportReason.DownloadError))
            }

            is BookmarkImportProcessor.ImportResult.Error.ParseError -> {
                _commands.emit(ExitFlowAsFailure(UserCannotImportReason.ErrorParsingBookmarks))
            }

            is BookmarkImportProcessor.ImportResult.Error.ImportError -> {
                _commands.emit(ExitFlowAsFailure(UserCannotImportReason.ErrorParsingBookmarks))
            }
        }
    }

    fun onCloseButtonPressed() {
        terminateFlowAsCancellation(webFlowStepObserver.getCurrentStep())
    }

    fun onBackButtonPressed(canGoBack: Boolean = false) {
        if (!canGoBack) {
            // if WebView can't go back, we should prompt user if they want to cancel the flow
            viewModelScope.launch { _commands.emit(PromptUserToConfirmFlowCancellation) }
        } else {
            _viewState.value = ViewState.NavigatingBack
        }
    }

    private fun terminateFlowAsCancellation(stage: String) {
        viewModelScope.launch {
            _viewState.value = ViewState.UserCancelledImportFlow(stage)
        }
    }

    fun firstPageLoading() {
        _viewState.value = ShowWebPage
    }

    suspend fun getReauthData(originalUrl: String): ReAuthenticationDetails? =
        withContext(dispatchers.io()) {
            if (canReAuthenticate()) {
                reauthenticationHandler.retrieveReauthData(originalUrl)
            } else {
                null
            }
        }

    private suspend fun canReAuthenticate(): Boolean =
        withContext(dispatchers.io()) {
            autofillFeature.canReAuthenticateGoogleLoginsAutomatically().isEnabled()
        }

    fun onStoredCredentialsAvailable(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        scenarioAllowsReAuthentication: Boolean,
    ) {
        viewModelScope.launch {
            logcat { "Bookmark import - onStoredCredentialsAvailable. re-AuthAllowed=$scenarioAllowsReAuthentication, triggerType=$triggerType" }
            val reauthData = if (scenarioAllowsReAuthentication) getReauthData(originalUrl) else null
            if (reauthData?.password != null) {
                logcat { "Stored credentials available but using re-authentication details instead: $reauthData" }
                _commands.emit(
                    Command.InjectCredentialsFromReauth(
                        url = originalUrl,
                        password = reauthData.password,
                    ),
                )
            } else {
                logcat { "No re-auth data available or permitted, prompting user to select stored credentials" }
                _commands.emit(
                    Command.PromptUserToSelectFromStoredCredentials(
                        originalUrl = originalUrl,
                        credentials = credentials,
                        triggerType = triggerType,
                    ),
                )
            }
        }
    }

    fun onNoStoredCredentialsAvailable(originalUrl: String) {
        viewModelScope.launch {
            logcat { "Bookmark import - onNoStoredCredentialsAvailable for $originalUrl" }
            _commands.emit(Command.NoCredentialsAvailable)
        }
    }

    fun onCredentialsAutofilled(
        originalUrl: String,
        password: String?,
    ) {
        logcat { "Bookmark import - credentials autofilled for $originalUrl" }
        reauthenticationHandler.storeForReauthentication(originalUrl, password)
    }

    fun onCredentialsAvailableToSave(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        logcat { "Bookmark import - credentials available to save for $currentUrl" }
        // Store credentials for potential re-use during this flow
        reauthenticationHandler.storeForReauthentication(currentUrl, credentials.password)
    }

    fun onPageStarted(url: String?) {
        webFlowStepObserver.updateStep(Step.UrlVisited(url))
        val host = url?.toUri()?.host ?: return
        _viewState.value = if (host.contains(TAKEOUT_ADDRESS, ignoreCase = true)) {
            HideWebPage
        } else if (host.contains(ACCOUNTS_ADDRESS, ignoreCase = true)) {
            ShowWebPage
        } else {
            ShowWebPage
        }
    }

    fun onWebMessageReceived(data: String) {
        viewModelScope.launch {
            when (val result = takeoutWebMessageParser.parseMessage(data)) {
                is TakeoutActionSuccess -> webFlowStepObserver.updateStep(JavascriptStep(result.actionID))
                is TakeoutActionError -> {
                    logcat(WARN) { "Bookmark-import: experienced an error in the step: $result, raw:$data" }
                    val step = result.actionID ?: "last-success-${webFlowStepObserver.getCurrentStep()}"
                    _commands.emit(ExitFlowAsFailure(WebAutomationError(step)))
                }
                is UnknownMessageFormat -> logcat(WARN) { "Bookmark-import: failed to parse message, unknown format: $data" }
            }
        }
    }

    companion object {
        private const val TAKEOUT_ADDRESS = "takeout.google.com"
        private const val ACCOUNTS_ADDRESS = "accounts.google.com"
    }

    sealed interface Command {
        data class InjectCredentialsFromReauth(
            val url: String? = null,
            val username: String = "",
            val password: String?,
        ) : Command

        data class PromptUserToSelectFromStoredCredentials(
            val originalUrl: String,
            val credentials: List<LoginCredentials>,
            val triggerType: LoginTriggerType,
        ) : Command

        data object PromptUserToConfirmFlowCancellation : Command

        data object NoCredentialsAvailable : Command

        data class ExitFlowWithSuccess(
            val importedCount: Int,
        ) : Command

        data class ExitFlowAsFailure(
            val reason: UserCannotImportReason,
        ) : Command
    }

    sealed interface ViewState {
        data object Initializing : ViewState

        data object ShowWebPage : ViewState

        data object HideWebPage : ViewState

        data class LoadingWebPage(
            val url: String,
        ) : ViewState

        data class UserCancelledImportFlow(
            val stage: String,
        ) : ViewState

        data class UserFinishedCannotImport(
            val reason: UserCannotImportReason,
        ) : ViewState

        data object NavigatingBack : ViewState

        data class ShowError(
            val reason: UserCannotImportReason,
        ) : ViewState
    }
}
