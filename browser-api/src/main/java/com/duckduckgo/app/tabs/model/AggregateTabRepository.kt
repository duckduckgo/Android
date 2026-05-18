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

package com.duckduckgo.app.tabs.model

import kotlinx.coroutines.flow.Flow

/**
 * Cross-mode view over [TabRepository]. Use this when consumers need to react to tabs
 * from both regular and fire mode at once — counting, stats, or any flow-based aggregation
 * that should not be tied to a single mode.
 *
 * For everything that operates on one mode at a time, inject the mode-qualified
 * [TabRepository] directly.
 */
interface AggregateTabRepository {

    /**
     * Emits the concatenation of regular-mode and fire-mode tabs. Re-emits whenever either
     * underlying repository's [TabRepository.flowTabs] emits.
     */
    val flowTabs: Flow<List<TabEntity>>
}
