package com.duckduckgo.savedsites.impl.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class FavouritesNewTabSettingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: FavouritesNewTabSettingsViewModel
    private val setting = FakeFeatureToggleFactory.create(NewTabFavouritesSectionSetting::class.java)
    private val lifecycleOwner: LifecycleOwner = mock()
    private val pixels: Pixel = mock()

    @Before
    fun setup() {
        testee = FavouritesNewTabSettingsViewModel(
            coroutinesTestRule.testDispatcherProvider,
            setting,
            pixels,
        )
    }

    @Test
    fun whenViewCreatedAndSettingEnabledThenViewStateUpdated() = runTest {
        setting.self().setRawStoredState(State(enable = true))
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.enabled)
            }
        }
    }

    @Test
    fun whenViewCreatedAndSettingDisabledThenViewStateUpdated() = runTest {
        setting.self().setRawStoredState(State(enable = false))
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.enabled)
            }
        }
    }

    @Test
    fun whenSettingEnabledThenPixelFired() = runTest {
        setting.self().setRawStoredState(State(enable = false))
        testee.onSettingEnabled(true)
        verify(pixels).fire(SavedSitesPixelName.FAVOURITES_SECTION_TOGGLED_ON)
    }

    @Test
    fun whenSettingDisabledThenPixelFired() = runTest {
        setting.self().setRawStoredState(State(enable = false))
        testee.onSettingEnabled(false)
        verify(pixels).fire(SavedSitesPixelName.FAVOURITES_SECTION_TOGGLED_OFF)
    }
}
