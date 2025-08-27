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

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import logcat.LogPriority.WARN
import logcat.logcat

@ContributesViewModel(FragmentScope::class)
class ImportGoogleBookmarksWebFlowViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    // TODO: Add bookmark importer when available
    // private val bookmarkImporter: BookmarkImporter,
    private val urlToStageMapper: ImportGoogleBookmarkUrlToStageMapper,
) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Initializing)
    val viewState: StateFlow<ViewState> = _viewState

    suspend fun loadInitialWebpage() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.value = ViewState.LoadingWebPage(TAKEOUT_BASE_URL)
        }
    }

    suspend fun onBookmarkZipAvailable(zipData: ByteArray) {
        // TODO: Parse and import bookmark zip when bookmark importer is available
        // when (val parseResult = bookmarkImporter.parseBookmarkZip(zipData)) {
        //     is BookmarkImportResult.Success -> onBookmarksParsed(parseResult)
        //     is BookmarkImportResult.Error -> onBookmarkError()
        // }

        // For now, just mark as successful
        _viewState.value = ViewState.UserFinishedImportFlow
    }

    private suspend fun onBookmarksParsed(/* parseResult: BookmarkImportResult.Success */) {
        // TODO: Import bookmarks when importer is available
        // bookmarkImporter.import(parseResult.bookmarksToImport, parseResult.numberBookmarksInSource)
        _viewState.value = ViewState.UserFinishedImportFlow
    }

    fun onBookmarkError() {
        logcat(WARN) { "Error decoding bookmark zip" }
        _viewState.value = ViewState.UserFinishedCannotImport(UserCannotImportReason.ErrorParsingBookmarks)
    }

    fun onCloseButtonPressed(url: String?) {
        terminateFlowAsCancellation(url ?: "unknown")
    }

    fun onBackButtonPressed(
        url: String? = null,
        canGoBack: Boolean = false,
    ) {
        // if WebView can't go back, then we're at the first stage or something's gone wrong. Either way, time to cancel out of the screen.
        if (!canGoBack) {
            terminateFlowAsCancellation(url ?: "unknown")
            return
        }

        _viewState.value = ViewState.NavigatingBack
    }

    private fun terminateFlowAsCancellation(url: String) {
        viewModelScope.launch {
            _viewState.value = ViewState.UserCancelledImportFlow(urlToStageMapper.getStage(url))
        }
    }

    fun firstPageLoading() {
        _viewState.value = ViewState.ShowWebPage
    }

    sealed interface ViewState {
        data object Initializing : ViewState
        data object ShowWebPage : ViewState
        data class LoadingWebPage(val url: String) : ViewState
        data class UserCancelledImportFlow(val stage: String) : ViewState
        data object UserFinishedImportFlow : ViewState
        data class UserFinishedCannotImport(val reason: UserCannotImportReason) : ViewState
        data object NavigatingBack : ViewState
        data class ShowError(val reason: UserCannotImportReason) : ViewState
    }

    sealed interface UserCannotImportReason : Parcelable {
        @Parcelize
        data object ErrorParsingBookmarks : UserCannotImportReason

        @Parcelize
        data object NetworkError : UserCannotImportReason

        @Parcelize
        data object DownloadError : UserCannotImportReason
    }

    sealed interface BackButtonAction {
        data object NavigateBack : BackButtonAction
    }

    companion object {
        private const val TAKEOUT_BASE_URL = "https://takeout.google.com"
    }
}
