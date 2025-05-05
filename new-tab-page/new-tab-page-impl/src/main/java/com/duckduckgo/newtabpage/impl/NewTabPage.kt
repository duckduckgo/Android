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

package com.duckduckgo.newtabpage.impl

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import com.duckduckgo.newtabpage.impl.view.NewTabPageView
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NewTabPagePlugin::class,
    priority = NewTabPagePlugin.PRIORITY_LEGACY_NTP,
    defaultActiveValue = DefaultFeatureValue.FALSE,
    supportExperiments = true,
    internalAlwaysEnabled = false,
)
class NewTabPage @Inject constructor() : NewTabPagePlugin {

    override fun getView(context: Context): View {
        return NewTabPageView(context)
    }
}
