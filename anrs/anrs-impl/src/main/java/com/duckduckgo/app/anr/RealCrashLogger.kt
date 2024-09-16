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

package com.duckduckgo.app.anr

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.anrs.store.UncaughtExceptionDao
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.checkMainThread
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealCrashLogger @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val uncaughtExceptionDao: UncaughtExceptionDao,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val customTabDetector: CustomTabDetector,
    @ProcessName private val processName: String,
) : CrashLogger {
    override fun logCrash(crash: CrashLogger.Crash) {
        checkMainThread()

        uncaughtExceptionDao.add(
            crash.asCrashEntity(
                appBuildConfig.versionName,
                processName,
                webViewVersionProvider.getFullVersion(),
                customTabDetector.isCustomTab(),
            ),
        )
    }
}
