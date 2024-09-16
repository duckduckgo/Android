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

package com.duckduckgo.networkprotection.impl.subscription.settings

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(200)
class ProSettingsNetP @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return ProSettingNetPView(context)
    }
}
