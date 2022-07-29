/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.store

import android.content.Context
import android.content.res.Configuration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AppTheme {
    fun getAppTheme(): DuckDuckGoTheme
    fun isLightModeEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class BrowserAppTheme @Inject constructor(
    private val context: Context,
    private val themeDataStore: ThemingDataStore
) : AppTheme {

    override fun getAppTheme(): DuckDuckGoTheme {
        return getAppTheme()
    }

    override fun isLightModeEnabled(): Boolean {
        return when (themeDataStore.theme) {
            DuckDuckGoTheme.LIGHT -> true
            DuckDuckGoTheme.DARK -> false
            DuckDuckGoTheme.SYSTEM_DEFAULT -> {
                !isNightMode(context)
            }
        }
    }

    private fun isNightMode(context: Context): Boolean {
        val nightModeMasked = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeMasked == Configuration.UI_MODE_NIGHT_YES
    }
}
