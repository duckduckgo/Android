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

package com.duckduckgo.app.browser.serviceworker

import android.os.Build
import android.webkit.ServiceWorkerController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import timber.log.Timber
import javax.inject.Inject
import dagger.SingleInstanceIn

@SingleInstanceIn(AppScope::class)
class ServiceWorkerLifecycleObserver @Inject constructor(
    private val requestInterceptor: RequestInterceptor,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val appBuildConfig: AppBuildConfig,
) : DefaultLifecycleObserver {
    @Suppress("NewApi") // we use appBuildConfig
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
            try {
                ServiceWorkerController.getInstance().setServiceWorkerClient(
                    BrowserServiceWorkerClient(requestInterceptor, uncaughtExceptionRepository)
                )
            } catch (e: Exception) {
                Timber.w(e.localizedMessage)
            }
        }
    }
}
