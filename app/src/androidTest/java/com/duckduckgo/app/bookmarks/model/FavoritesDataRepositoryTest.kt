/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import org.mockito.kotlin.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import dagger.Lazy
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class FavoritesDataRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }
    private lateinit var db: AppDatabase
    private lateinit var favoritesDao: FavoritesDao
    private lateinit var repository: FavoritesRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        favoritesDao = db.favoritesDao()
        repository = FavoritesDataRepository(favoritesDao, lazyFaviconManager)
    }

    @Test
    fun whenInsertFavoriteThenReturnSavedSite() {
        givenNoFavoritesStored()

        val savedSite = repository.insert("title", "http://example.com")

        assertEquals("title", savedSite.title)
        assertEquals("http://example.com", savedSite.url)
        assertEquals(1, savedSite.position)
    }

    @Test
    fun whenInsertFavoriteWithoutTitleThenSavedSiteUsesUrlAsTitle() {
        givenNoFavoritesStored()

        val savedSite = repository.insert("", "http://example.com")

        assertEquals("http://example.com", savedSite.title)
        assertEquals("http://example.com", savedSite.url)
        assertEquals(1, savedSite.position)
    }

    @Test
    fun whenUserHasFavoritesAndInsertFavoriteThenSavedSiteUsesNextPosition() {
        givenMoreFavoritesStored()

        val savedSite = repository.insert("Favorite", "http://favexample.com")

        assertEquals("Favorite", savedSite.title)
        assertEquals("http://favexample.com", savedSite.url)
        assertEquals(2, savedSite.position)
    }

    @Test
    fun whenDataSourceChangesThenNewListReceived() {
        givenNoFavoritesStored()

        repository.insert("Favorite", "http://favexample.com")

        val testObserver = repository.favoritesObservable().test()
        val lastState = testObserver.assertNoErrors().values().last()
        assertEquals(1, lastState.size)
        assertEquals(Favorite(1, "Favorite", "http://favexample.com", 1), lastState.first())
    }

    @Test
    fun whenFavoriteUpdatedThenDatabaseChanged() {
        val favorite = Favorite(1, "Favorite", "http://favexample.com", 1)
        givenFavorite(favorite)
        val updatedFavorite = favorite.copy(position = 3)

        repository.update(updatedFavorite)

        assertFavoriteExistsInDb(updatedFavorite)
    }

    @Test
    fun whenListReceivedThenUpdateItemsWithNewPositionInDatabase() {
        val favorite = Favorite(1, "Favorite", "http://favexample.com", 1)
        val favorite2 = Favorite(2, "Favorite2", "http://favexample2.com", 2)
        givenFavorite(favorite, favorite2)

        repository.updateWithPosition(listOf(favorite2, favorite))

        assertFavoriteExistsInDb(favorite2.copy(position = 0))
        assertFavoriteExistsInDb(favorite.copy(position = 1))
    }

    @Test
    fun whenFavoriteDeletedThenDatabaseUpdated() = runTest {
        val favorite = Favorite(1, "Favorite", "http://favexample.com", 1)
        givenFavorite(favorite)

        repository.delete(favorite)

        assertNull(favoritesDao.favorite(favorite.id))
        verify(mockFaviconManager).deletePersistedFavicon(favorite.url)
    }

    @Test
    fun whenUserHasFavoritesThenReturnTrue() = runTest {
        val favorite = Favorite(1, "Favorite", "http://favexample.com", 1)
        givenFavorite(favorite)

        assertTrue(repository.userHasFavorites())
    }

    @Test
    fun whenFavoriteByUrlRequestedAndAvailableThenReturnFavorite() = runTest {
        val favorite = Favorite(id = 1, title = "title", url = "www.website.com", position = 1)
        val otherFavorite = Favorite(id = 2, title = "other title", url = "www.other-website.com", position = 2)

        repository.insert(favorite)
        repository.insert(otherFavorite)

        val result = repository.favorite("www.website.com")

        assertEquals(favorite, result)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNotAvailableThenReturnNull() = runTest {
        val favorite = Favorite(id = 1, title = "title", url = "www.website.com", position = 1)
        val otherFavorite = Favorite(id = 2, title = "other title", url = "www.other-website.com", position = 2)

        repository.insert(favorite)
        repository.insert(otherFavorite)

        val result = repository.favorite("www.test.com")

        assertNull(result)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNoFavoritesAvailableThenReturnNull() = runTest {
        val result = repository.favorite("www.test.com")

        assertNull(result)
    }

    private fun givenFavorite(vararg favorite: Favorite) {
        favorite.forEach {
            favoritesDao.insert(FavoriteEntity(it.id, it.title, it.url, it.position))
        }
    }

    private fun givenMoreFavoritesStored() {
        favoritesDao.insert(FavoriteEntity(title = "title", url = "http://example.com", position = 0))
        favoritesDao.insert(FavoriteEntity(title = "title 2", url = "http://other.com", position = 1))
    }

    private fun givenNoFavoritesStored() {
        assertNull(favoritesDao.getLastPosition())
    }

    private fun assertFavoriteExistsInDb(favorite: Favorite) {
        val storedFavorite = favoritesDao.favorite(favorite.id) ?: error("Favorite not found in database")
        assertEquals(storedFavorite.title, favorite.title)
        assertEquals(storedFavorite.url, favorite.url)
        assertEquals(storedFavorite.position, favorite.position)
    }
}
