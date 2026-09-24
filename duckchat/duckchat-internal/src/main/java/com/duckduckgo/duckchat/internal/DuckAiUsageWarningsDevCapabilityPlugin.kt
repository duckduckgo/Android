/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.internal

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckAiUsageWarningsDevCapabilityPlugin @Inject constructor(
    private val context: Context,
) : DuckAiDevCapabilityPlugin {
    override fun title(): String = context.getString(R.string.devSettingsDuckAiUsageWarningsCapabilityTitle)
    override fun subtitle(): String = context.getString(R.string.devSettingsDuckAiUsageWarningsCapabilitySubtitle)
    override fun onCapabilityClicked(activityContext: Context) {
        activityContext.startActivity(DuckAiUsageWarningsDevActivity.intent(activityContext))
    }
}
