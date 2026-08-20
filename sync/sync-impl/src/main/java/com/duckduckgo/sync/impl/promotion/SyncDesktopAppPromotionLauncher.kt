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

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams
import com.duckduckgo.desktopapppromotion.api.PixelConfig
import com.duckduckgo.desktopapppromotion.api.PixelFireSpec
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.GET_OTHER_DEVICES_SCREEN_LAUNCH_SOURCE
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Sends the user to either the shared desktop-app promo screen or Sync's own multi-platform screen.
 * The choice is made here, at the point of launch, so the screen the user lands on is the first one
 * they see.
 */
interface SyncDesktopAppPromotionLauncher {
    suspend fun launch(
        context: Context,
        source: SyncGetOnOtherPlatformsLaunchSource,
    )
}

@ContributesBinding(AppScope::class)
class RealSyncDesktopAppPromotionLauncher @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val settingsPageFeature: SettingsPageFeature,
    private val dispatchers: DispatcherProvider,
) : SyncDesktopAppPromotionLauncher {

    override suspend fun launch(
        context: Context,
        source: SyncGetOnOtherPlatformsLaunchSource,
    ) {
        val desktopBrowserPromoEnabled = withContext(dispatchers.io()) {
            settingsPageFeature.newDesktopBrowserSettingEnabled().isEnabled()
        }

        val params = if (desktopBrowserPromoEnabled) {
            desktopAppPromotionParams(context, source)
        } else {
            SyncGetOnOtherPlatformsParams(source)
        }

        globalActivityStarter.start(context, params)
    }

    private fun desktopAppPromotionParams(
        context: Context,
        source: SyncGetOnOtherPlatformsLaunchSource,
    ): DesktopAppPromotionParams {
        val sourceParams = hashMapOf(GET_OTHER_DEVICES_SCREEN_LAUNCH_SOURCE to source.value)

        return DesktopAppPromotionParams(
            toolbarTitle = context.getString(R.string.syncGetAppsOnOtherPlatformsActivityTitle),
            title = context.getString(R.string.syncGetAppsOnOtherPlatformsTitle),
            body = context.getString(R.string.syncGetAppsOnOtherPlatformInstruction),
            illustration = CommonR.drawable.ic_app_download_128,
            downloadUrl = DESKTOP_BROWSER_URL,
            shareIntentTitle = context.getString(R.string.syncGetAppsOnOtherPlatforms),
            showDismissButton = false,
            pixels = PixelConfig(
                impression = PixelFireSpec(SyncPixelName.SYNC_GET_OTHER_DEVICES_SCREEN_SHOWN.pixelName, sourceParams),
                shareClicked = PixelFireSpec(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_SHARED.pixelName, sourceParams),
                linkClicked = PixelFireSpec(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_COPIED.pixelName, sourceParams),
            ),
        )
    }

    companion object {
        private const val DESKTOP_BROWSER_URL = "https://duckduckgo.com/browser?origin=funnel_browser_android_sync"
    }
}
