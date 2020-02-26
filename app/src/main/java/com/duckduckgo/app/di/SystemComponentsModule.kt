/*
 * Copyright (c) 2020 DuckDuckGo
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
import android.content.pm.PackageManager
import com.duckduckgo.app.systemsearch.DeviceAppListProvider
import com.duckduckgo.app.systemsearch.DeviceAppLookup
import com.duckduckgo.app.systemsearch.InstalledDeviceAppListProvider
import com.duckduckgo.app.systemsearch.InstalledDeviceAppLookup
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
open class SystemComponentsModule {

    @Singleton
    @Provides
    fun packageManager(context: Context): PackageManager = context.packageManager

    @Singleton
    @Provides
    fun deviceAppsListProvider(packageManager: PackageManager): DeviceAppListProvider = InstalledDeviceAppListProvider(packageManager)

    @Provides
    @Singleton
    fun deviceAppLookup(deviceAppListProvider: DeviceAppListProvider): DeviceAppLookup = InstalledDeviceAppLookup(deviceAppListProvider)
}