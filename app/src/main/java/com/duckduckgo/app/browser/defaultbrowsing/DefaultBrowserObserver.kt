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

package com.duckduckgo.app.browser.defaultbrowsing

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter

class DefaultBrowserObserver(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
) : MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        val isDefaultBrowser = defaultBrowserDetector.isDefaultBrowser()
        if (appInstallStore.defaultBrowser != isDefaultBrowser) {
            appInstallStore.defaultBrowser = isDefaultBrowser
            when {
                isDefaultBrowser -> {
                    val params = mapOf(
                        PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to false.toString(),
                    )
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_SET, params)
                }
                else -> pixel.fire(AppPixelName.DEFAULT_BROWSER_UNSET)
            }
        }
    }
}
