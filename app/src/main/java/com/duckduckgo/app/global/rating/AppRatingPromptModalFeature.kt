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

package com.duckduckgo.app.global.rating

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Routes the app enjoyment / rating prompt through the prompts coordinator instead of firing it
 * directly on app start.
 *
 * When enabled (the default), [AppRatingPromptEvaluator] owns the decision and the prompt competes
 * with the other modals at priority 6. Disabling it reverts to [AppEnjoymentAppCreationObserver]
 * deciding the prompt type on every app start, unarbitrated.
 *
 * Exactly one of the two paths runs, so the prompt can never be evaluated twice in a single start.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "appRatingPromptModal",
)
interface AppRatingPromptModalFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
