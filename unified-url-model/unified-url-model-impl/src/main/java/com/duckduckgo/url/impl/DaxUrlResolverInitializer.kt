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
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import javax.inject.Inject

/**
 * Fallback installer for the app-scoped [DaxUrlProvider] into the process-wide [DaxUrlResolver], so
 * that `FeatureException.daxDomain` (a non-injectable data-class property) can resolve through it.
 *
 * The primary install happens earlier, in `DuckDuckGoApplication.onMainProcessCreate` right after the
 * DI graph is built. This observer re-installs the same app-scoped singleton at `ON_CREATE` as a
 * safety net; [DaxUrlResolver.install] is idempotent, so running twice is harmless.
 *
 * Neither install point is load-bearing for correctness: `FeatureException.daxDomain` caches only
 * non-null results and retries on null, so any read that races installation self-heals on the next
 * access. Early install merely shrinks the window in which that read returns null.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
class DaxUrlResolverInitializer @Inject constructor(
    private val daxUrlProvider: DaxUrlProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        DaxUrlResolver.install(daxUrlProvider)
    }
}
