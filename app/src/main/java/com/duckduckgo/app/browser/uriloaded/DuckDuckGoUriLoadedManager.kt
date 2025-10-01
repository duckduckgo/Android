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

package com.duckduckgo.app.browser.uriloaded

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface UriLoadedManager {
    fun sendUriLoadedPixel()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = UriLoadedManager::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class DuckDuckGoUriLoadedManager @Inject constructor(
    private val pixel: Pixel,
    private val uriLoadedPixelFeature: UriLoadedPixelFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess isMainProcess: Boolean,
) : UriLoadedManager, PrivacyConfigCallbackPlugin {

    private var shouldSendUriLoadedPixel: Boolean = false

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun sendUriLoadedPixel() {
        if (shouldSendUriLoadedPixel) {
            pixel.fire(AppPixelName.URI_LOADED)
        }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    private fun loadToMemory() {
        shouldSendUriLoadedPixel = uriLoadedPixelFeature.self().isEnabled()
    }
}
