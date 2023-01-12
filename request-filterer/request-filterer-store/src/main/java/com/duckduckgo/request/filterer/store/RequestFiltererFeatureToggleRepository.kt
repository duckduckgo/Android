/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.request.filterer.store

import android.content.Context

interface RequestFiltererFeatureToggleRepository : RequestFiltererFeatureToggleStore {
    companion object {
        fun create(
            context: Context,
        ): RequestFiltererFeatureToggleRepository {
            val store = RealRequestFiltererFeatureToggleStore(context)
            return RealRequestFiltererFeatureToggleRepository(store)
        }
    }
}

class RealRequestFiltererFeatureToggleRepository constructor(
    private val requestFiltererFeatureToggleStore: RequestFiltererFeatureToggleStore,
) : RequestFiltererFeatureToggleRepository, RequestFiltererFeatureToggleStore by requestFiltererFeatureToggleStore
