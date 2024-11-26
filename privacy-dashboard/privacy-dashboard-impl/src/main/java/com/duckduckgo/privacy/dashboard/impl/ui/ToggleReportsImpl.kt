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

package com.duckduckgo.privacy.dashboard.impl.ui

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.api.ui.ToggleReports
import com.duckduckgo.privacy.dashboard.impl.ToggleReportsDataStore
import com.duckduckgo.privacy.dashboard.impl.ToggleReportsFeature
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class ToggleReportsImpl @Inject constructor(
    private val toggleReportsFeature: ToggleReportsFeature,
    private val toggleReportsDataStore: ToggleReportsDataStore,
) : ToggleReports {
    override suspend fun shouldPrompt(): Boolean {
        return (toggleReportsFeature.self().isEnabled() && toggleReportsDataStore.canPrompt())
    }

    override suspend fun onPromptDismissed() {
        toggleReportsDataStore.insertTogglePromptDismiss()
    }

    override suspend fun onReportSent() {
        toggleReportsDataStore.insertTogglePromptSend()
    }
}
