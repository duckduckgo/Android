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

package com.duckduckgo.macos_impl

import android.content.Context
import android.content.Intent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_api.MacOsNav
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MacOsNavImpl @Inject constructor() : MacOsNav {
    override fun openMacOsSettings(activityContext: Context): Intent {
        return MacOsActivity.intent(activityContext)
    }
}
