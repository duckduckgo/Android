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

package com.duckduckgo.autofill.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.credential.saving.DuckAddressLoginCreator
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat

@ContributesBinding(FragmentScope::class)
class RealDuckAddressLoginCreator @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
) : DuckAddressLoginCreator {

    override fun createLoginForPrivateDuckAddress(
        duckAddress: String,
        tabId: String,
        originalUrl: String,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!canCreateLoginForThisSite(originalUrl)) {
                return@launch
            }

            val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
            if (autologinId == null) {
                saveDuckAddressForCurrentSite(duckAddress = duckAddress, tabId = tabId, url = originalUrl)
            } else {
                val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
                if (existingAutoSavedLogin == null) {
                    logcat(WARN) { "Can't find saved login with autosavedLoginId: $autologinId" }
                    saveDuckAddressForCurrentSite(duckAddress = duckAddress, tabId = tabId, url = originalUrl)
                } else {
                    updateUsernameIfDifferent(existingAutoSavedLogin, duckAddress)
                }
            }
        }
    }

    private suspend fun canCreateLoginForThisSite(originalUrl: String): Boolean {
        // this could be triggered from email autofill, which might happen even if saving passwords is disabled so need to guard here
        if (!autofillCapabilityChecker.canSaveCredentialsFromWebView(originalUrl)) {
            return false
        }

        // if the user said to never save for this site, we don't want to auto-save a login for a private duck address on it
        if (neverSavedSiteRepository.isInNeverSaveList(originalUrl)) {
            return false
        }

        return true
    }

    private suspend fun updateUsernameIfDifferent(
        autosavedLogin: LoginCredentials,
        username: String,
    ) {
        if (username == autosavedLogin.username) {
            logcat(INFO) { "Generated username matches existing login; nothing to do here" }
        } else {
            logcat(INFO) { "Updating existing login with new username. Login id is: ${autosavedLogin.id}" }
            autofillStore.updateCredentials(autosavedLogin.copy(username = username))
        }
    }

    private suspend fun saveDuckAddressForCurrentSite(
        duckAddress: String,
        tabId: String,
        url: String,
    ) {
        val credentials = LoginCredentials(domain = url, username = duckAddress, password = null)
        autofillStore.saveCredentials(rawUrl = url, credentials = credentials)?.id?.let { savedId ->
            logcat(INFO) { "New login saved for duck address $duckAddress on site $url because no exact matches were found with ID: $savedId" }
            autoSavedLoginsMonitor.setAutoSavedLoginId(savedId, tabId)
        }
    }
}
