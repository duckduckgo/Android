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

package com.duckduckgo.savedsites.impl.sync

import android.content.*
import android.view.*
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.api.*
import com.squareup.anvil.annotations.*
import javax.inject.*

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(100)
class DisplayModeSyncSettingsPlugin @Inject constructor() : SyncSettingsPlugin {
    override fun getView(context: Context): View {
        return DisplayModeSyncSetting(context)
    }
}
