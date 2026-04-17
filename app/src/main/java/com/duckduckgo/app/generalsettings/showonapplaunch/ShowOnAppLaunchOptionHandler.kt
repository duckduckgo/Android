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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.isHttpOrHttps
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface ShowOnAppLaunchOptionHandler {
    suspend fun handleAfterInactivityOption(wasIdle: Boolean)
    suspend fun handleAppLaunchOption()
    suspend fun handleResolvedUrlStorage(
        currentUrl: String?,
        isRootOfTab: Boolean,
        tabId: String,
    )
}

@ContributesBinding(AppScope::class)
class ShowOnAppLaunchOptionHandlerImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val tabRepository: TabRepository,
    private val appBuildConfig: AppBuildConfig,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
) : ShowOnAppLaunchOptionHandler {

    override suspend fun handleAfterInactivityOption(wasIdle: Boolean) {
        // new users see New Tab
        logcat { "FirstScreen: Inactivity Timer passed" }
        if (appBuildConfig.isNewInstall() && !showOnAppLaunchOptionDataStore.hasOptionSelected()) {
            logcat { "FirstScreen: setting New Tab for new users" }
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)
        }
        // existing users see whatever they had selected
        applyShowOnAppLaunchOption(fromInactivity = wasIdle)
    }

    override suspend fun handleAppLaunchOption() {
        applyShowOnAppLaunchOption(fromInactivity = false)
    }

    private suspend fun applyShowOnAppLaunchOption(fromInactivity: Boolean) {
        val option = showOnAppLaunchOptionDataStore.optionFlow.first()
        logcat { "FirstScreen: showing $option on app launch" }
        when (option) {
            LastOpenedTab -> Unit
            NewTabPage -> {
                val selectedTab = tabRepository.getSelectedTab()
                if (selectedTab == null || !selectedTab.url.isNullOrBlank()) {
                    if (fromInactivity) {
                        ntpAfterIdleManager.onIdleReturnTriggered()
                    }
                    tabRepository.add()
                }
            }
            is SpecificPage -> handleSpecificPageOption(option)
        }
    }

    override suspend fun handleResolvedUrlStorage(
        currentUrl: String?,
        isRootOfTab: Boolean,
        tabId: String,
    ) {
        withContext(dispatchers.io()) {
            val shouldSaveCurrentUrlForShowOnAppLaunch = currentUrl != null &&
                isRootOfTab &&
                tabId == showOnAppLaunchOptionDataStore.showOnAppLaunchTabId

            if (shouldSaveCurrentUrlForShowOnAppLaunch) {
                showOnAppLaunchOptionDataStore.setResolvedPageUrl(currentUrl!!)
            }
        }
    }

    private suspend fun handleSpecificPageOption(option: SpecificPage) {
        val userUri = option.url.toUri()
        val resolvedUri = option.resolvedUrl?.toUri()

        val urls = listOfNotNull(userUri, resolvedUri).map { uri ->
            stripIfHttpOrHttps(uri)
        }

        val tabIdUrlMap = getTabIdUrlMap(tabRepository.flowTabs.first())

        val existingTabId = tabIdUrlMap.entries.findLast { it.value in urls }?.key

        if (existingTabId != null) {
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchTabId(existingTabId)
            tabRepository.select(existingTabId)
        } else {
            val tabId = tabRepository.add(url = option.url)
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchTabId(tabId)
        }
    }

    private fun stripIfHttpOrHttps(uri: Uri): String {
        return if (uri.isHttpOrHttps) {
            stripUri(uri)
        } else {
            uri.toString()
        }
    }

    private fun stripUri(uri: Uri): String = uri.run {
        val authority = uri.authority?.removePrefix("www.")
        uri.buildUpon()
            .scheme(null)
            .authority(authority)
            .toString()
            .replaceFirst("//", "")
    }

    private fun getTabIdUrlMap(tabs: List<TabEntity>): Map<String, String> {
        return tabs
            .filterNot { tab -> tab.url.isNullOrBlank() }
            .associate { tab ->
                val tabUri = tab.url!!.toUri()
                val strippedUrl = stripIfHttpOrHttps(tabUri)
                tab.tabId to strippedUrl
            }
    }
}
