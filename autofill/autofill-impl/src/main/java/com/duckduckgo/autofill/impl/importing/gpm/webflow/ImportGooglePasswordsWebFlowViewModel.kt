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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.ShowingWebContent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class ImportGooglePasswordsWebFlowViewModel @Inject constructor(
    private val pixel: Pixel,
) : ViewModel() {

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val _viewState = MutableStateFlow<ViewState>(ShowingWebContent)
    val viewState: StateFlow<ViewState> = _viewState

    fun onPageStarted(url: String?) {
        Timber.i("onPageStarted: $url")
    }

    fun onPageFinished(url: String?) {
        _viewState.value = ShowingWebContent
        Timber.i("onPageFinished: $url")
    }

    fun onBackButtonPressed(
        url: String?,
        canGoBack: Boolean,
    ) {
        Timber.v("onBackButtonPressed: %s, canGoBack=%s", url, canGoBack)

        // if WebView can't go back, then we're at the first stage or something's gone wrong. Either way, time to cancel out of the screen.
        if (!canGoBack) {
            terminateFlowAsCancellation(url ?: "unknown")
            return
        }

        _viewState.value = ViewState.NavigatingBack
    }

    private fun terminateFlowAsCancellation(stage: String) {
        _viewState.value = ViewState.UserCancelledImportFlow(stage)
    }

    fun loadedStartingUrl() {
        // pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_MODAL_DISPLAYED)
    }

    fun onCloseButtonPressed(url: String?) {
        terminateFlowAsCancellation(url ?: "unknown")
    }

    sealed interface ViewState {
        data object ShowingWebContent : ViewState
        data class UserCancelledImportFlow(val stage: String) : ViewState
        data object NavigatingBack : ViewState
    }

    sealed interface BackButtonAction {
        data object NavigateBack : BackButtonAction
    }

    companion object {
    }
}
