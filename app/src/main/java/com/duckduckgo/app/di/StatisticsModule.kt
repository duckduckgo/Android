/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import android.content.Context
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.statistics.api.*
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.common.utils.device.ContextDeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object StatisticsModule {

    @Provides
    fun offlinePixelSender(
        offlinePixels: DaggerSet<OfflinePixel>,
    ): OfflinePixelSender = OfflinePixelSender(offlinePixels)

    @Provides
    fun deviceInfo(context: Context): DeviceInfo = ContextDeviceInfo(context)

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun pixelDao(database: AppDatabase): PendingPixelDao {
        return database.pixelDao()
    }
}
