/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.customtabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class CustomTabViewModel @Inject constructor(
    private val customTabDetector: CustomTabDetector,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    data class ViewState(
        val tabId: String = "${CUSTOM_TAB_NAME_PREFIX}${UUID.randomUUID()}",
        val url: String? = null,
        val toolbarColor: Int = 0,
    )

    fun onCustomTabCreated(url: String?, toolbarColor: Int) {
        viewModelScope.launch(dispatcherProvider.io()) {
            _viewState.emit(
                viewState.value.copy(
                    url = url,
                    toolbarColor = toolbarColor,
                ),
            )
            pixel.fire(CustomTabPixelNames.CUSTOM_TABS_OPENED)
        }
    }

    fun onShowCustomTab() {
        logcat { "Show Custom Tab with tabId=${viewState.value.tabId}" }
        customTabDetector.setCustomTab(true)
    }

    fun onCloseCustomTab() {
        logcat { "Close Custom Tab with tabId=${viewState.value.tabId}" }
        customTabDetector.setCustomTab(false)
    }

    companion object {
        const val CUSTOM_TAB_NAME_PREFIX = "CustomTab-"
    }
}
