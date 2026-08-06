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

import kotlin.properties.ReadOnlyProperty

/**
 * Backs a property with the one axis the Legacy/New split used to encode as separate subtypes: when
 * [memoize] is true the value is computed once and cached; when false it is recomputed on every read
 * (mirroring today's re-parse-per-call). Each call returns an independent delegate, so every property
 * gets its own cache. Both modes return identical values — only the repeated work differs — so the
 * `useUnifiedUrl` flag selects between them as a pure performance A/B.
 */
internal fun <T> held(memoize: Boolean, compute: () -> T): ReadOnlyProperty<Any?, T> =
    if (memoize) {
        val cached = lazy(LazyThreadSafetyMode.PUBLICATION, compute)
        ReadOnlyProperty { _, _ -> cached.value }
    } else {
        ReadOnlyProperty { _, _ -> compute() }
    }
