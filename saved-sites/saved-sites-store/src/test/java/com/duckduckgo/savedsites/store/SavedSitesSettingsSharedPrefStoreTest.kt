package com.duckduckgo.savedsites.store

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.NATIVE
import com.duckduckgo.savedsites.store.SavedSitesSettingsSharedPrefStore.Companion.FILENAME
import com.duckduckgo.savedsites.store.SavedSitesSettingsSharedPrefStore.Companion.KEY_FAVORITES_DISPLAY_MODE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SavedSitesSettingsSharedPrefStoreTest {

    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var prefs: SharedPreferences
    private val mockContext: Context = mock()

    private lateinit var testee: SavedSitesSettingsSharedPrefStore

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        whenever(mockContext.getSharedPreferences(FILENAME, 0)).thenReturn(prefs)
        testee = SavedSitesSettingsSharedPrefStore(mockContext, coroutinesTestRule.testScope, coroutinesTestRule.testDispatcherProvider)
    }

    @Test
    fun whenGetFavoritesDisplayModeThenReturnStoredValue() = runTest {
        prefs.edit().putString(KEY_FAVORITES_DISPLAY_MODE, NATIVE.value).commit()

        assertEquals(NATIVE, testee.favoritesDisplayMode)
    }

    @Test
    fun whenNoValueThenReturnNative() = runTest {
        assertEquals(NATIVE, testee.favoritesDisplayMode)
    }

    @Test
    fun whenValueChangedThenFlowEmitsNewValue() = runTest {
        prefs.edit().putString(KEY_FAVORITES_DISPLAY_MODE, NATIVE.value).commit()
        testee.favoritesFormFactorModeFlow().test {
            assertEquals(NATIVE, awaitItem())
            testee.favoritesDisplayMode = FavoritesDisplayMode.UNIFIED
            assertEquals(FavoritesDisplayMode.UNIFIED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
