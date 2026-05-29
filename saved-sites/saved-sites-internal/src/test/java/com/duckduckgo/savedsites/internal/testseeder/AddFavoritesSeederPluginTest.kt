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

package com.duckduckgo.savedsites.internal.testseeder

import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.testseeder.api.TestSeederKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class AddFavoritesSeederPluginTest {

    private val repository: SavedSitesRepository = mock()
    private val plugin = AddFavoritesSeederPlugin(repository)

    @Test
    fun whenSingleBareDomainThenInsertsOneFavoriteWithHttpsScheme() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, "reddit.com")

        verify(repository).insertFavorite(url = "https://reddit.com", title = "reddit.com")
    }

    @Test
    fun whenMultipleBareDomainsThenInsertsEachInOrder() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, "reddit.com;eff.org;cnn.com")

        inOrder(repository).run {
            verify(repository).insertFavorite(url = "https://reddit.com", title = "reddit.com")
            verify(repository).insertFavorite(url = "https://eff.org", title = "eff.org")
            verify(repository).insertFavorite(url = "https://cnn.com", title = "cnn.com")
        }
    }

    @Test
    fun whenUrlHasHttpsSchemeThenPreservesScheme() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, "https://www.reddit.com/r/foo")

        verify(repository).insertFavorite(
            url = "https://www.reddit.com/r/foo",
            title = "www.reddit.com",
        )
    }

    @Test
    fun whenUrlHasHttpSchemeThenPreservesScheme() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, "http://example.com")

        verify(repository).insertFavorite(url = "http://example.com", title = "example.com")
    }

    @Test
    fun whenValuesHaveSurroundingWhitespaceThenTrimmed() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, " reddit.com ; eff.org ")

        verify(repository).insertFavorite(url = "https://reddit.com", title = "reddit.com")
        verify(repository).insertFavorite(url = "https://eff.org", title = "eff.org")
    }

    @Test
    fun whenTrailingSemicolonThenEmptyEntriesIgnored() = runTest {
        plugin.apply(TestSeederKey.ADD_FAVORITES.key, "reddit.com;")

        verify(repository).insertFavorite(url = "https://reddit.com", title = "reddit.com")
    }

    @Test
    fun whenValueIsEmptyThenThrowsAndDoesNotTouchRepository() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { plugin.apply(TestSeederKey.ADD_FAVORITES.key, "") }
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun whenValueIsOnlySemicolonsThenThrowsAndDoesNotTouchRepository() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { plugin.apply(TestSeederKey.ADD_FAVORITES.key, ";;;") }
        }
        verifyNoInteractions(repository)
    }
}
