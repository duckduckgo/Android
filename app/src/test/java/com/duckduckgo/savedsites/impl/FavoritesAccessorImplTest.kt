package com.duckduckgo.savedsites.impl

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeSavedSitesSettingsRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import junit.framework.TestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesAccessorImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var testee: FavoritesAccessorImpl
    private val savedSitesSettingsRepository = FakeSavedSitesSettingsRepository()

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()
        testee = FavoritesAccessorImpl(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSitesSettingsRepository,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDefaultThenReturnFavoritesFromRootFolderFlow() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.getFavorites().test {
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun whenUnifiedModeThenReturnFavoritesFromRootFolderFlow() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.getFavorites().test {
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun whenNativeModeThenReturnMobileFavoritesFolderFlow() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.getFavorites().test {
            assertEquals(2, awaitItem().size)
        }
    }

    @Test
    fun whenViewModeChangesThenNewFlowEmitted() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.getFavorites().test {
            assertEquals(2, awaitItem().size)
            savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
            assertEquals(3, awaitItem().size)
        }
    }

    @Test
    fun whenDefaultThenReturnRootFavoritesFolderList() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.getFavoritesSync().size == 3)
    }

    @Test
    fun whenUnifiedModeThenReturnRootFavoritesFolderList() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.getFavoritesSync().size == 3)
    }

    @Test
    fun whenNativeModeThenReturnMobileFavoritesFolderList() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.getFavoritesSync().size == 2)
    }

    @Test
    fun whenGetFavoritesCountByDomainThenOnlyCheckRootFolder() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.getFavoritesCountByDomain("favexample.com") == 3)
    }

    @Test
    fun whenUnifiedModeGetFavoritesCountByDomainThenOnlyCheckRootFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.getFavoritesCountByDomain("favexample.com") == 3)
    }

    @Test
    fun whenNativeModeGetFavoritesCountByDomainThenOnlyCheckMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)
        assertTrue(testee.getFavoritesCountByDomain("favexample.com") == 2)
    }

    @Test
    fun whenGetFavoriteByIdThenOnlyCheckRootFolder() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertNotNull(testee.getFavoriteById("favorite3"))
    }

    @Test
    fun whenUnifiedModeGetFavoriteByIdThenOnlyCheckRootFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertNotNull(testee.getFavoriteById("favorite3"))
    }

    @Test
    fun whenNativeModeGetFavoriteByIdThenOnlyCheckMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertNull(testee.getFavoriteById("favorite3"))
    }

    @Test
    fun whenFavoritesCountThenOnlyCheckRootFolder() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.favoritesCount() == 3L)
    }

    @Test
    fun whenUnifiedModeFavoritesCountThenOnlyCheckRootFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.favoritesCount() == 3L)
    }

    @Test
    fun whenNativeModeFavoritesCountThenOnlyCheckMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        assertTrue(testee.favoritesCount() == 2L)
    }

    @Test
    fun whenUpdateWithPositionThenUpdateItemsOnRootFolder() {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.updateWithPosition(listOf(favoritethree, favoritetwo, favoriteone))

        var entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)
        assertEquals(entitiesInFolder.get(0).entityId, favoritethree.id)
        assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
        assertEquals(entitiesInFolder.get(2).entityId, favoriteone.id)

        entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT)
        TestCase.assertEquals(entitiesInFolder.get(0).entityId, favoriteone.id)
        TestCase.assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
    }

    @Test
    fun whenUnifiedModeUpdateWithPositionThenUpdateItemsOnRootFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.updateWithPosition(listOf(favoritethree, favoritetwo, favoriteone))

        var entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)
        assertEquals(entitiesInFolder.get(0).entityId, favoritethree.id)
        assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
        assertEquals(entitiesInFolder.get(2).entityId, favoriteone.id)

        entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT)
        TestCase.assertEquals(entitiesInFolder.get(0).entityId, favoriteone.id)
        TestCase.assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
    }

    @Test
    fun whenNativeModeUpdateWithPositionThenUpdateItemsOnRootFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite2", "Favorite2", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite3", "Favorite3", "http://favexample.com", "timestamp", 2)
        givenFavoriteStored(favoriteone, favoritetwo, favoritethree, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoritetwo, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.updateWithPosition(listOf(favoritethree, favoritetwo, favoriteone))

        var entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)
        assertEquals(entitiesInFolder.get(0).entityId, favoriteone.id)
        assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
        assertEquals(entitiesInFolder.get(2).entityId, favoritethree.id)

        entitiesInFolder = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT)
        TestCase.assertEquals(entitiesInFolder.get(0).entityId, favoritethree.id)
        TestCase.assertEquals(entitiesInFolder.get(1).entityId, favoritetwo.id)
    }

    @Test
    fun whenInsertFavoriteThenUpdateItemsOnRootFolder() {
        testee.insertFavorite("favorite1", "Favorite", "http://favexample.com", "timestamp")

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).size == 1)
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).size == 0)
    }

    @Test
    fun whenUnifiedModeInsertFavoriteThenUpdateItemsOnRootAndMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        testee.insertFavorite("favorite1", "Favorite", "http://favexample.com", "timestamp")

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).size == 1)
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).size == 1)
    }

    @Test
    fun whenNativeModeInsertFavoriteThenUpdateItemsOnRootAndMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        testee.insertFavorite("favorite1", "Favorite", "http://favexample.com", "timestamp")

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).size == 1)
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).size == 1)
    }

    @Test
    fun whenDeleteFavoriteThenDeleteFromRootFolder() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_DESKTOP_ROOT)

        testee.deleteFavorite(favoriteone)

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).size == 1)
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_DESKTOP_ROOT).size == 1)
    }

    @Test
    fun whenUnifiedModeDeleteFavoriteThenDeleteFromAllFolders() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.UNIFIED)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_DESKTOP_ROOT)

        testee.deleteFavorite(favoriteone)

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_DESKTOP_ROOT).isEmpty())
    }

    @Test
    fun whenNativeModeDeleteDesktopFavoriteThenDeleteFromMobileFolder() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_DESKTOP_ROOT)

        testee.deleteFavorite(favoriteone)

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).size == 1)
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_DESKTOP_ROOT).size == 1)
    }

    @Test
    fun whenNativeModeDeleteNonDesktopFavoriteThenDeleteFromAllFolders() = runTest {
        savedSitesSettingsRepository.setViewMode(FavoritesViewMode.NATIVE)
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_ROOT)
        givenFavoriteStored(favoriteone, favoriteFolderId = SavedSitesNames.FAVORITES_MOBILE_ROOT)

        testee.deleteFavorite(favoriteone)

        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT).isEmpty())
        assertTrue(savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_DESKTOP_ROOT).isEmpty())
    }

    @Test
    fun whenDataSourceChangesThenNewListReceived() {
        givenNoFavoritesStored()

        testee.insertFavorite(id = "Favorite1", title = "Favorite", url = "http://favexample.com", lastModified = "timestamp")

        val testObserver = testee.getFavoritesObservable().test()
        val lastState = testObserver.assertNoErrors().values().last()

        Assert.assertEquals(1, lastState.size)
        Assert.assertEquals("Favorite", lastState.first().title)
        Assert.assertEquals("http://favexample.com", lastState.first().url)
        Assert.assertEquals(0, lastState.first().position)
    }

    private fun givenFavoriteStored(vararg favorite: Favorite, favoriteFolderId: String) {
        favorite.forEach {
            val entity = Entity(it.id, it.title, it.url, type = BOOKMARK, lastModified = it.lastModified)
            savedSitesEntitiesDao.insert(entity)
            savedSitesRelationsDao.insert(Relation(folderId = favoriteFolderId, entityId = entity.entityId))
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
        }
    }

    private fun givenNoFavoritesStored() {
        assertFalse(testee.favoritesCount() > 0)
    }
}
