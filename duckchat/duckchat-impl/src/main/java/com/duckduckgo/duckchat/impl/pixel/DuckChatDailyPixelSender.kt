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

package com.duckduckgo.duckchat.impl.pixel

import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class DuckChatDailyPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    @UiThread
    override fun onStart(owner: LifecycleOwner) {
        coroutineScope.launch(dispatcherProvider.io()) {
            pixel.fire(
                pixel = DuckChatPixelName.DUCK_CHAT_IS_ENABLED_DAILY,
                parameters = mapOf(PixelParameter.IS_ENABLED to duckChatFeatureRepository.isDuckChatUserEnabled().toString()),
                type = Daily(),
            )
            pixel.fire(
                pixel = DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY,
                parameters = mapOf(PixelParameter.IS_ENABLED to duckChatFeatureRepository.shouldShowInBrowserMenu().toString()),
                type = Daily(),
            )
            pixel.fire(
                pixel = DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY,
                parameters = mapOf(PixelParameter.IS_ENABLED to duckChatFeatureRepository.shouldShowInAddressBar().toString()),
                type = Daily(),
            )
            pixel.fire(
                pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY,
                parameters = mapOf(PixelParameter.IS_ENABLED to duckChatFeatureRepository.isInputScreenUserSettingEnabled().toString()),
                type = Daily(),
            )
        }
    }
}
