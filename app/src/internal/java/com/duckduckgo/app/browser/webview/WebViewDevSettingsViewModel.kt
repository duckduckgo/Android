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

package com.duckduckgo.app.browser.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class WebViewDevSettingsViewModel @Inject constructor(
    private val webViewInformationExtractor: WebViewInformationExtractor,
) : ViewModel() {

    data class ViewState(
        val webViewVersion: String = "unknown",
        val webViewPackage: String = "unknown",
    )

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun start() {
        viewModelScope.launch {
            val webViewData = webViewInformationExtractor.extract()

            viewState.emit(
                currentViewState().copy(
                    webViewVersion = webViewData.webViewVersion,
                    webViewPackage = webViewData.webViewPackageName,
                ),
            )
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
