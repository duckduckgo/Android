/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.pdf

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
class PdfHandlerComponentToggler @Inject constructor(
    private val context: Context,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) { sync() }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatchers.io()) { sync() }
    }

    fun sync() {
        val shouldEnable = appBuildConfig.sdkInt >= 31 && androidBrowserConfigFeature.pdfViewer().isEnabled()
        context.packageManager.setComponentEnabledSetting(
            ComponentName(appBuildConfig.applicationId, PDF_HANDLER_ALIAS),
            if (shouldEnable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        private const val PDF_HANDLER_ALIAS = "com.duckduckgo.app.dispatchers.PdfViewerHandler"
    }
}
