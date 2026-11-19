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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.view.View
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NewChatHintsRenderer
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealNewChatHintsRenderer @Inject constructor(
    private val feature: DuckChatFeature,
    private val dispatchers: DispatcherProvider,
) : NewChatHintsRenderer {
    override suspend fun create(context: Context, tabId: String): View? {
        val enabled = withContext(dispatchers.io()) { feature.newChatHints().isEnabled() }
        return if (enabled) NewChatHintsView(context).apply { this.tabId = tabId } else null
    }
}
