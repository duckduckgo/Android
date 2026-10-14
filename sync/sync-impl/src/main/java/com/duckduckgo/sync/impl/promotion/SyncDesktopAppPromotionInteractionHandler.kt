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

package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.GET_OTHER_DEVICES_SCREEN_LAUNCH_SOURCE
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_ACTIVATING
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_DISABLED
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_ENABLED
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Inject

class SyncDesktopAppPromotionInteractionHandler @Inject constructor(
    private val source: SyncGetOnOtherPlatformsLaunchSource,
    private val pixel: Pixel,
) : DesktopAppPromotionInteractionHandler {

    override val handlerId: String = handlerId(source)

    override suspend fun onInteraction(interaction: Interaction): Boolean {
        val params = mapOf(GET_OTHER_DEVICES_SCREEN_LAUNCH_SOURCE to source.value)
        return when (interaction) {
            Interaction.IMPRESSION -> {
                pixel.fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_SCREEN_SHOWN, params)
                true
            }
            Interaction.SHARE_CLICKED -> {
                pixel.fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_SHARED, params)
                true
            }
            Interaction.LINK_COPIED -> {
                pixel.fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_COPIED, params)
                true
            }
            Interaction.SHARE_COMPLETED, Interaction.DISMISSED -> true
        }
    }

    companion object {
        fun handlerId(source: SyncGetOnOtherPlatformsLaunchSource) = "sync_get_other_devices_${source.value}"
    }
}

@Module
@ContributesTo(AppScope::class)
object SyncDesktopAppPromotionInteractionHandlerModule {

    @Provides
    @IntoSet
    fun provideActivatingHandler(pixel: Pixel): DesktopAppPromotionInteractionHandler =
        SyncDesktopAppPromotionInteractionHandler(SOURCE_ACTIVATING, pixel)

    @Provides
    @IntoSet
    fun provideDisabledHandler(pixel: Pixel): DesktopAppPromotionInteractionHandler =
        SyncDesktopAppPromotionInteractionHandler(SOURCE_SYNC_DISABLED, pixel)

    @Provides
    @IntoSet
    fun provideEnabledHandler(pixel: Pixel): DesktopAppPromotionInteractionHandler =
        SyncDesktopAppPromotionInteractionHandler(SOURCE_SYNC_ENABLED, pixel)
}
