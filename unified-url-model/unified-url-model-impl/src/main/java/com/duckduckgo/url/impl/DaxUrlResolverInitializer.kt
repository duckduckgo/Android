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

package com.duckduckgo.url.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.url.DaxUrlProvider
import com.duckduckgo.url.DaxUrlResolver
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Installs the app-scoped [DaxUrlProvider] into the process-wide [DaxUrlResolver] so that
 * `FeatureException.url` (a non-injectable data-class property) can resolve through it.
 *
 * `onCreate` is early enough: `FeatureException.url` is only read during URL matching (page
 * navigation), which always happens after process `ON_CREATE`. Parsing/populating exceptions never
 * evaluates the getter.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class DaxUrlResolverInitializer @Inject constructor(
    private val daxUrlProvider: DaxUrlProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        DaxUrlResolver.install(daxUrlProvider)
    }
}
