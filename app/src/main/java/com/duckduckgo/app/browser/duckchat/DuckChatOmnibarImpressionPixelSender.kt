/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.duckchat

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface DuckChatOmnibarImpressionPixelSender {
    fun sendImpressionPixel(isVisible: Boolean)
}

@ContributesBinding(AppScope::class)
class RealDuckChatOmnibarImpressionPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckChatOmnibarImpressionPixelSender {

    private var lastVisible = false
    private var pendingJob: Job? = null

    override fun sendImpressionPixel(isVisible: Boolean) {
        if (isVisible && !lastVisible) {
            pendingJob?.cancel()
            pendingJob = appCoroutineScope.launch(dispatcherProvider.io()) {
                delay(IMPRESSION_DELAY_MS)
                pixel.fire(DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE.pixelName)
            }
        } else if (!isVisible) {
            pendingJob?.cancel()
            pendingJob = null
        }
        lastVisible = isVisible
    }

    private companion object {
        private const val IMPRESSION_DELAY_MS = 300L
    }
}
