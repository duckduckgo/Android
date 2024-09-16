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

package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle

@ContributesRemoteFeature(
    scope = AppScope::class,
    boundType = SyncPromotionFeature::class,
    featureName = "syncPromotion",
)
/**
 * Feature flags for sync promotions. Sync promotions can appear in various places in the app.
 * Note, that it probably doesn't make sense to show a sync promotion if the sync feature flag is disabled.
 * But the sync feature flag is independent of this, and should be checked separately.
 */
interface SyncPromotionFeature {

    @Toggle.InternalAlwaysEnabled
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.InternalAlwaysEnabled
    @Toggle.DefaultValue(false)
    fun bookmarks(): Toggle

    @Toggle.InternalAlwaysEnabled
    @Toggle.DefaultValue(false)
    fun passwords(): Toggle
}
