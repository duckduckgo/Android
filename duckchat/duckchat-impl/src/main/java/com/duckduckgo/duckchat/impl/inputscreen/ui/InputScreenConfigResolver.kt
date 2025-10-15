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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenBrowserButtonsConfig
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.navigation.api.getActivityParams
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface InputScreenConfigResolver {
    val isTopOmnibar: Boolean

    fun shouldShowInstalledApps(): Boolean

    fun useTopBar(): Boolean

    fun mainButtonsEnabled(): Boolean
}

@ContributesBinding(scope = ActivityScope::class)
@SingleInstanceIn(scope = ActivityScope::class)
class InputScreenConfigResolverImpl @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    private val appCompatActivity: AppCompatActivity,
) : InputScreenConfigResolver {
    companion object {
        fun useTopBar(
            isTopOmnibar: Boolean,
            duckChatInternal: DuckChatInternal,
        ): Boolean = isTopOmnibar || !duckChatInternal.inputScreenBottomBarEnabled.value
    }

    override val isTopOmnibar: Boolean by lazy {
        appCompatActivity.intent.getActivityParams(InputScreenActivityParams::class.java)?.isTopOmnibar ?: true
    }

    override fun shouldShowInstalledApps(): Boolean {
        val params = appCompatActivity.intent?.getActivityParams(InputScreenActivityParams::class.java)
        return params?.showInstalledApps ?: false
    }

    override fun useTopBar(): Boolean =
        useTopBar(
            isTopOmnibar = isTopOmnibar,
            duckChatInternal = duckChatInternal,
        ) || appCompatActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun mainButtonsEnabled(): Boolean {
        val browserButtonsConfig = appCompatActivity.intent.getActivityParams(InputScreenActivityParams::class.java)?.browserButtonsConfig
        return duckChatInternal.showMainButtonsInInputScreen.value && browserButtonsConfig is InputScreenBrowserButtonsConfig.Enabled
    }
}
