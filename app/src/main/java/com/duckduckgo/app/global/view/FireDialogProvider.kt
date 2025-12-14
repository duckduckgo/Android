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

package com.duckduckgo.app.global.view

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.DataClearing
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

interface FireDialogProvider {
    fun createFireDialog(context: Context): FireDialog
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class FireDialogLauncherImpl @Inject constructor() : FireDialogProvider {

    @Inject
    lateinit var clearDataAction: ClearDataAction

    @Inject
    lateinit var dataClearing: DataClearing

    @Inject
    lateinit var androidBrowserConfigFeature: AndroidBrowserConfigFeature

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var userEventsStore: UserEventsStore

    @AppCoroutineScope
    @Inject
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var fireButtonStore: FireButtonStore

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    override fun createFireDialog(context: Context): FireDialog = FireDialog(
        context = context,
        clearDataAction = clearDataAction,
        dataClearing = dataClearing,
        androidBrowserConfigFeature = androidBrowserConfigFeature,
        pixel = pixel,
        settingsDataStore = settingsDataStore,
        userEventsStore = userEventsStore,
        appCoroutineScope = appCoroutineScope,
        dispatcherProvider = dispatcherProvider,
        fireButtonStore = fireButtonStore,
        appBuildConfig = appBuildConfig,
    )
}
