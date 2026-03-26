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

package com.duckduckgo.autofill.impl.engagement

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AutofillRefreshRetentionListener @Inject constructor(
    private val engagementRepository: AutofillEngagementRepository,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        coroutineScope.launch(dispatchers.io()) {
            engagementRepository.recordSearchedToday()
        }
    }
}
