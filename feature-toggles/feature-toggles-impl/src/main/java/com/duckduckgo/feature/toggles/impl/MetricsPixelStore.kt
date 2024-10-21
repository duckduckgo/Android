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

package com.duckduckgo.feature.toggles.impl

import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.checkMainThread
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface MetricsPixelStore {
    /**
     * @return true if the pixel with the given tag can be fired or false otherwise
     */
    @WorkerThread
    fun wasPixelFired(tag: String): Boolean

    /**
     * Stores the tag [String] passed as parameter
     */
    @WorkerThread
    fun storePixelTag(tag: String)
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = MetricsPixelStore::class,
)
class RealMetricsPixelStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val appBuildConfig: AppBuildConfig,
) : MetricsPixelStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override fun storePixelTag(tag: String) {
        if (appBuildConfig.isInternalBuild()) {
            checkMainThread()
        }

        preferences.edit { putBoolean(tag, true) }
    }

    override fun wasPixelFired(tag: String): Boolean {
        if (appBuildConfig.isInternalBuild()) {
            checkMainThread()
        }

        val didExecuteAlready = preferences.getBoolean(tag, false)
        return didExecuteAlready
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.feature.toggles.pixels.prefs"
    }
}
