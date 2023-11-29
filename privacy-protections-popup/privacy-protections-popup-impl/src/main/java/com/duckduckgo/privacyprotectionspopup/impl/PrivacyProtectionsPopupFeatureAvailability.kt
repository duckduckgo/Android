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

package com.duckduckgo.privacyprotectionspopup.impl

import android.content.Context
import android.content.pm.ApplicationInfo
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PrivacyProtectionsPopupFeatureAvailability {
    suspend fun isAvailable(): Boolean
}

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupFeatureAvailabilityImpl @Inject constructor(
    private val context: Context,
) : PrivacyProtectionsPopupFeatureAvailability {

    override suspend fun isAvailable(): Boolean {
        // TODO
        return context.isAppDebuggable
    }
}

private val Context.isAppDebuggable: Boolean
    get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
