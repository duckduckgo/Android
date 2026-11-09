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
import android.widget.Toast
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsUsage
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class DuckAiNewChatHintsResetCapabilityPlugin @Inject constructor(
    private val context: Context,
    private val usage: NewChatHintsUsage,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiDevCapabilityPlugin {
    override fun title(): String = context.getString(R.string.devSettingsDuckAiNewChatHintsResetTitle)
    override fun subtitle(): String = context.getString(R.string.devSettingsDuckAiNewChatHintsResetSubtitle)
    override fun onCapabilityClicked(activityContext: Context) {
        val appContext = activityContext.applicationContext
        appCoroutineScope.launch(dispatchers.io()) {
            usage.reset()
            withContext(dispatchers.main()) {
                Toast.makeText(appContext, R.string.devSettingsDuckAiNewChatHintsResetDone, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
