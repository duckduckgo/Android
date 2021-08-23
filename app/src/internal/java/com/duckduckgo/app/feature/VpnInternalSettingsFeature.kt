/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.feature

import android.content.Context
import androidx.core.content.ContextCompat
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.vpn.internal.feature.VpnInternalSettingsActivity
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class VpnInternalSettingsFeature @Inject constructor() : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "AppTP dev settings"
    }

    override fun internalFeatureSubtitle(): String {
        return "AppTP dev settings for internal users"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        activityContext.startActivity(VpnInternalSettingsActivity.intent(activityContext))
    }
}