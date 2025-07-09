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

package com.duckduckgo.app.dispatchers

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.global.intentText
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.setup.SyncUrlIdentifier
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class IntentDispatcherViewModel @Inject constructor(
    private val customTabDetector: CustomTabDetector,
    private val dispatcherProvider: DispatcherProvider,
    private val emailProtectionLinkVerifier: EmailProtectionLinkVerifier,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val syncUrlIdentifier: SyncUrlIdentifier,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    data class ViewState(
        val customTabRequested: Boolean = false,
        val intentText: String? = null,
        val toolbarColor: Int = 0,
        val isExternal: Boolean = false,
    )

    fun onIntentReceived(intent: Intent?, defaultColor: Int, isExternal: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            runCatching {
                val hasSession = intent?.hasExtra(CustomTabsIntent.EXTRA_SESSION) == true
                val intentText = intent?.intentText
                val toolbarColor = intent?.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, defaultColor) ?: defaultColor
                val isEmailProtectionLink = emailProtectionLinkVerifier.shouldDelegateToInContextView(intentText, true)
                val isDuckDuckGoUrl = intentText?.let { duckDuckGoUrlDetector.isDuckDuckGoUrl(it) } ?: false

                val isSyncPairingUrl = syncUrlIdentifier.shouldDelegateToSyncSetup(intentText)
                val customTabRequested = hasSession && !isEmailProtectionLink && !isDuckDuckGoUrl && !isSyncPairingUrl

                logcat { "Intent $intent received. Has extra session=$hasSession. Intent text=$intentText. Toolbar color=$toolbarColor" }

                customTabDetector.setCustomTab(false)
                _viewState.emit(
                    viewState.value.copy(
                        customTabRequested = customTabRequested,
                        intentText = if (customTabRequested) intentText?.sanitize() else intentText,
                        toolbarColor = toolbarColor,
                        isExternal = isExternal,
                    ),
                )
            }.onFailure {
                logcat(WARN) { "Error handling custom tab intent: ${it.message}" }
            }
        }
    }

    private fun String.sanitize(): String {
        if (this.startsWith("http://") || this.startsWith("https://")) {
            // Some apps send URLs with spaces in the intent. This is happening mostly for authorization URLs.
            // E.g https://mastodon.social/oauth/authorize?client_id=AcfPDZlcKUjwIatVtMt8B8cmdW-w1CSOR6_rYS_6Kxs&scope=read write push&redirect_uri=mastify://oauth&response_type=code
            return this.replace(" ", "%20")
        }
        return this
    }
}
