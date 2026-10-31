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

package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiSubscriptionEvents
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckAiSubscriptionEvents @Inject constructor(
    private val promoCampaignJSHelper: PromoCampaignJSHelper,
) : DuckAiSubscriptionEvents {
    override fun observe(): Flow<SubscriptionEventData> = promoCampaignJSHelper.promoRecordEvents
}
