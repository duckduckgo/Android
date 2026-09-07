/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.widget

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class WidgetPrivacyConfigUpdateListener @Inject constructor(
    private val context: Context,
    private val widgetUpdater: WidgetUpdater,
    private val searchWidgetProviderInfoUpdater: SearchWidgetProviderInfoUpdater,
    private val appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles,
) : PrivacyConfigCallbackPlugin, MainProcessLifecycleObserver {

    private var lastAppliedAddressBarEnabled: Boolean? = null

    override fun onStart(owner: LifecycleOwner) {
        val isAddressBarEnabled = appBrandDesignUpdateToggles.addressBar().isEnabled()
        if (isAddressBarEnabled == lastAppliedAddressBarEnabled) return

        refreshWidgets(isAddressBarEnabled)
    }

    override fun onPrivacyConfigDownloaded() {
        refreshWidgets(appBrandDesignUpdateToggles.addressBar().isEnabled())
    }

    private fun refreshWidgets(isAddressBarEnabled: Boolean) {
        searchWidgetProviderInfoUpdater.sync()
        widgetUpdater.updateWidgets(context)
        lastAppliedAddressBarEnabled = isAddressBarEnabled
    }
}
