/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.di.scopes.AppScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(AppScope::class)
class AuditSettingsViewModel @Inject constructor(
    private val userAllowListDao: UserWhitelistDao,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    data class ViewState(
        val nextTdsEnabled: Boolean = false,
        val startupTraceEnabled: Boolean = false,
    )

    sealed class Command {
        data class GoToUrl(val url: String) : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun goToUrl(
        url: String,
        protectionsEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            if (protectionsEnabled) {
                reAddProtections()
            } else {
                removeProtections()
            }
            command.send(Command.GoToUrl(url))
        }
    }

    fun onDestroy() {
        viewModelScope.launch {
            reAddProtections()
        }
    }

    private suspend fun removeProtections() {
        withContext(dispatchers.io()) {
            domainsUsed.map {
                userAllowListDao.insert(it)
            }
        }
    }

    private suspend fun reAddProtections() {
        withContext(dispatchers.io()) {
            domainsUsed.map {
                userAllowListDao.delete(it)
            }
        }
    }

    companion object {
        const val STEP_1 = "https://cnn.com"
        const val STEP_2 = "https://gizmodo.com"
        const val STEP_3 = "https://httpbin.org/basic-auth/u/pw"
        const val REQUEST_BLOCKING = "https://privacy-test-pages.glitch.me/privacy-protections/request-blocking/?run"
        const val HTTPS_UPGRADES = "http://privacy-test-pages.glitch.me/privacy-protections/https-upgrades/?run"
        const val FIRE_BUTTON_STORE = "https://privacy-test-pages.glitch.me/privacy-protections/storage-blocking/?store"
        const val FIRE_BUTTON_RETRIEVE = "https://privacy-test-pages.glitch.me/privacy-protections/storage-blocking/?retrive"
        const val COOKIES_3P_STORE = "https://privacy-test-pages.glitch.me/privacy-protections/storage-blocking/?store"
        const val COOKIES_3P_RETRIEVE = "https://privacy-test-pages.glitch.me/privacy-protections/storage-blocking/?retrive"
        const val GPC = "https://privacy-test-pages.glitch.me/privacy-protections/gpc/?run"
        const val GPC_OTHER = "https://global-privacy-control.glitch.me/"
        const val SURROGATES = "https://privacy-test-pages.glitch.me/privacy-protections/surrogates/"
        val domainsUsed = listOf("privacy-test-pages.glitch.me", "privacy-test-pages.glitch.me")
    }
}
