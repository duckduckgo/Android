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

package com.duckduckgo.app.dev.settings

import android.content.Context
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class DevSettingsFeature @Inject constructor() : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Developer Settings"
    }

    override fun internalFeatureSubtitle(): String {
        return "Features useful for devs and debugging"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        activityContext.startActivity(DevSettingsActivity.intent(activityContext))
    }
}
