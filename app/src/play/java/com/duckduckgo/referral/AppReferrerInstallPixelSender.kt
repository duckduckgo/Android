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

package com.duckduckgo.referral

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class)
class AppReferrerInstallPixelSender @Inject constructor(
    private val appReferrerDataStore: AppReferrerDataStore,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val statisticsDataStore: StatisticsDataStore,
) : AtbLifecyclePlugin {

    private val pixelSent = AtomicBoolean(false)

    override fun onAppAtbInitialized() {
        Timber.v("AppReferrerInstallPixelSender: onAppAtbInitialized")
        sendPixelIfUnsent()
    }

    private fun sendPixelIfUnsent() {
        if (pixelSent.compareAndSet(false, true)) {
            appCoroutineScope.launch(dispatchers.io()) {
                sendOriginAttribute(appReferrerDataStore.utmOriginAttributeCampaign)
            }
        }
    }

    private fun sendOriginAttribute(originAttribute: String?) {
        val returningUser = statisticsDataStore.variant == RETURNING_USER_VARIANT

        val params = mutableMapOf(
            PIXEL_PARAM_LOCALE to appBuildConfig.deviceLocale.toLanguageTag(),
            PIXEL_PARAM_RETURNING_USER to returningUser.toString(),
        )

        // if origin is null, pixel is sent with origin omitted
        if (originAttribute != null) {
            params[PIXEL_PARAM_ORIGIN] = originAttribute
        }

        pixel.fire(pixel = AppPixelName.REFERRAL_INSTALL_UTM_CAMPAIGN, type = Pixel.PixelType.Unique(), parameters = params)
    }

    companion object {
        private const val RETURNING_USER_VARIANT = "ru"

        const val PIXEL_PARAM_ORIGIN = "origin"
        const val PIXEL_PARAM_LOCALE = "locale"
        const val PIXEL_PARAM_RETURNING_USER = "reinstall"
    }
}
