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

package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealPrivacyProUnifiedFeedback @Inject constructor(
    private val subscriptions: Subscriptions,
    private val privacyProFeature: PrivacyProFeature,
) : PrivacyProUnifiedFeedback {
    override suspend fun shouldUseUnifiedFeedback(source: PrivacyProFeedbackSource): Boolean {
        return when (source) {
            DDG_SETTINGS -> privacyProFeature.useUnifiedFeedback().isEnabled() && subscriptions.getSubscriptionStatus().isActive()
            else -> privacyProFeature.useUnifiedFeedback().isEnabled()
        }
    }
}
