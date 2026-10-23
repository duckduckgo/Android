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

package com.duckduckgo.app.anr.internal.setting

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.duckduckgo.app.anr.internal.R
import com.duckduckgo.app.anr.internal.feature.CrashAnrDevCapabilityPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
class TriggerAnrPlugin @Inject constructor(
    private val context: Context,
) : CrashAnrDevCapabilityPlugin {
    override fun title(): String = context.getString(R.string.trigger_anr_plugin_title)
    override fun subtitle(): String = context.getString(R.string.trigger_anr_plugin_subtitle)
    override fun onCapabilityClicked(activityContext: Context) {
        Handler(Looper.getMainLooper()).post {
            Thread.sleep(10000)
        }
    }
}
