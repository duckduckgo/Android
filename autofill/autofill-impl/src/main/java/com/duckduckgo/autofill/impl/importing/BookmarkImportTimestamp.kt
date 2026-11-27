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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

interface BookmarkImportTimestamp {
    fun onBookmarksImported()

    fun lastBookmarksImportMillis(): Long?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InMemoryBookmarkImportTimestamp @Inject constructor(
    private val timeProvider: TimeProvider,
) : BookmarkImportTimestamp {

    @Volatile
    private var lastBookmarksImportMillis: Long? = null

    override fun onBookmarksImported() {
        lastBookmarksImportMillis = timeProvider.currentTimeMillis()
    }

    override fun lastBookmarksImportMillis(): Long? = lastBookmarksImportMillis
}
