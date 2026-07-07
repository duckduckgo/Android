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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Non-internal flavors do not ship page-load tracing (it's only readable via a profileable/shell
 * trace, which is internal-only), so this binding is a no-op and androidx.tracing is not linked.
 */
@ContributesBinding(AppScope::class)
class NoOpPageLoadTracer @Inject constructor() : PageLoadTracer {
    override fun beginAsyncSection(name: String): Int = 0
    override fun endAsyncSection(name: String, cookie: Int) = Unit
}
