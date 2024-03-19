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

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.intentText
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class IntentDispatcherViewModel @Inject constructor(
    private val customTabDetector: CustomTabDetector,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    data class ViewState(
        val customTabRequested: Boolean = false,
        val intentText: String? = null,
        val toolbarColor: Int = 0,
    )

    fun onIntentReceived(intent: Intent?) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val hasSession = intent?.hasExtra(CustomTabsIntent.EXTRA_SESSION) == true
            val intentText = intent?.intentText
            val toolbarColor = intent?.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0) ?: 0

            Timber.d("Intent $intent received. Has extra session=$hasSession. Intent text=$intentText. Toolbar color=$toolbarColor")

            customTabDetector.setCustomTab(false)
            _viewState.emit(
                viewState.value.copy(
                    customTabRequested = hasSession,
                    intentText = intentText,
                    toolbarColor = toolbarColor,
                ),
            )
        }
    }
}
