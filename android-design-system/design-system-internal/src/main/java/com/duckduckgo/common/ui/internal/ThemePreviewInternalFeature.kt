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

package com.duckduckgo.common.ui.internal

import android.content.Context
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.internal.ui.AppComponentsActivity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesMultibinding(AppScope::class)
@PriorityKey(InternalFeaturePlugin.ADS_SETTINGS_PRIO_KEY)
class ThemesPreviewInternalFeature @Inject constructor(private val context: Context) : InternalFeaturePlugin {

    override fun internalFeatureTitle(): String {
        return context.getString(CommonR.string.ads_demo_plugin_title)
    }

    override fun internalFeatureSubtitle(): String {
        return context.getString(CommonR.string.ads_demo_plugin_message)
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        activityContext.startActivity(AppComponentsActivity.Companion.intent(activityContext))
    }
}
