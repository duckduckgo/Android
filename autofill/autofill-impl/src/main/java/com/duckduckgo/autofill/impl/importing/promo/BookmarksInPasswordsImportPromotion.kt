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

package com.duckduckgo.autofill.impl.importing.promo

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.ImportFromGoogleAvailability
import com.duckduckgo.autofill.impl.importing.BookmarkImportTimestamp
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

interface BookmarksInPasswordsImportPromotion {
    suspend fun canShowPromotion(importedPasswordsCount: Int): Boolean
}

@ContributesBinding(AppScope::class, binding = binding<BookmarksInPasswordsImportPromotion>())
class RealBookmarksInPasswordsImportPromotion @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val autofillFeature: AutofillFeature,
    private val importFromGoogleAvailability: ImportFromGoogleAvailability,
    private val bookmarkImportTimestamp: BookmarkImportTimestamp,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : BookmarksInPasswordsImportPromotion {

    override suspend fun canShowPromotion(importedPasswordsCount: Int): Boolean {
        if (importedPasswordsCount == 0) return false
        if (hasRecentlyImportedBookmarks()) return false

        return coroutineScope {
            val preconditions = listOf(
                async { isPromotionEnabled() },
                async { importFromGoogleAvailability.isBookmarksImportAvailable() },
                async { hasFewSavedBookmarks() },
            ).awaitAll()
            preconditions.all { it }
        }
    }

    private suspend fun isPromotionEnabled(): Boolean = withContext(dispatchers.io()) {
        autofillFeature.self().isEnabled() && autofillFeature.showCrossFeaturePromo().isEnabled()
    }

    private suspend fun hasFewSavedBookmarks(): Boolean = withContext(dispatchers.io()) {
        savedSitesRepository.bookmarksCount() < MAX_SAVED_BOOKMARKS
    }

    private fun hasRecentlyImportedBookmarks(): Boolean {
        val lastBookmarksImport = bookmarkImportTimestamp.lastBookmarksImportMillis() ?: return false
        return timeProvider.currentTimeMillis() - lastBookmarksImport < RECENT_IMPORT_WINDOW.inWholeMilliseconds
    }

    private companion object {
        const val MAX_SAVED_BOOKMARKS = 9
        val RECENT_IMPORT_WINDOW = 15.minutes
    }
}
