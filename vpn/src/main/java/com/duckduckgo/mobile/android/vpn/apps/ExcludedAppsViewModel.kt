/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Provider

class ExcludedAppsViewModel(
    private val deviceShieldExcludedApps: DeviceShieldExcludedApps
) : ViewModel() {

    internal suspend fun getExcludedApps() = flow {
        val (otherApps, appsWithIssues) = deviceShieldExcludedApps.getExclusionAppList()
            .filterNot { it.isDdgApp }
            .partition { BROWSERS.contains(it.packageName) }
        emit(ViewState(appsWithIssues, otherApps))
    }

    companion object {
        private val BROWSERS = listOf(
            "com.duckduckgo.mobile.android",
            "com.duckduckgo.mobile.android.debug",
            "com.duckduckgo.mobile.android.vpn",
            "com.duckduckgo.mobile.android.vpn.debug",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.android.browser",
            "com.sec.android.app.sbrowser",
            "info.guardianproject.orfo",
            "org.torproject.torbrowser_alpha",
            "mobi.mgeek.TunnyBrowser",
            "com.linkbubble.playstore",
            "org.adblockplus.browser",
            "arun.com.chromer",
            "com.flynx",
            "com.ghostery.android.ghostery",
            "com.cliqz.browser",
            "com.opera.mini.native",
            "com.uc.browser.en",
            "com.chrome.beta",
            "org.mozilla.firefox_beta",
            "com.opera.browser.beta",
            "com.opera.mini.native.beta",
            "com.sec.android.app.sbrowser.beta",
            "org.mozilla.fennec_fdroid",
            "org.mozilla.rocket",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.fennec_aurora",
            "org.mozilla.fennec",
            "com.google.android.apps.chrome",
            "org.chromium.chrome",
            "com.microsoft.bing",
            "com.yahoo.mobile.client.android.search",
            "com.google.android.apps.searchlite",
            "com.baidu.searchbox",
            "ru.yandex.searchplugin",
            "com.ecosia.android",
            "com.qwant.liberty",
            "com.qwantjunior.mobile",
            "com.nhn.android.search",
            "cz.seznam.sbrowser",
            "com.coccoc.trinhduyet"
        )
    }
}

internal data class ViewState(val appsWithIssues: List<VpnExcludedInstalledAppInfo>, val otherApps: List<VpnExcludedInstalledAppInfo>)

@ContributesMultibinding(AppObjectGraph::class)
class ExcludedAppsViewModelFactory @Inject constructor(
    private val deviceShieldExcludedApps: Provider<DeviceShieldExcludedApps>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(ExcludedAppsViewModel::class.java) -> (
                    ExcludedAppsViewModel(
                        deviceShieldExcludedApps.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
