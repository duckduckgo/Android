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

package com.duckduckgo.app.widget

import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.nextsteps.api.NextStepCompletionCheck
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class WidgetNextStepCompletionCheck @Inject constructor(
    private val widgetCapabilities: WidgetCapabilities,
) : NextStepCompletionCheck {
    override val itemId = "setup_add_widget"

    override suspend fun isComplete(): Boolean = widgetCapabilities.hasInstalledWidgets
}
