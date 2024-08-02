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

package com.duckduckgo.app.feedback

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AppFeedback {
    suspend fun shouldUseUnifiedFeedback(): Boolean
}

@ContributesBinding(ActivityScope::class)
class RealAppFeedback @Inject constructor(
    private val subscriptions: Subscriptions,
    private val appFeedbackRemoteFeature: AppFeedbackRemoteFeature,
) : AppFeedback {
    override suspend fun shouldUseUnifiedFeedback(): Boolean =
        appFeedbackRemoteFeature.useUnifiedFeedback().isEnabled() && subscriptions.getSubscriptionStatus().isActive()
}
