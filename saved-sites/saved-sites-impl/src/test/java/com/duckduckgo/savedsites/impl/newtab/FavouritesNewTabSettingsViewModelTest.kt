package com.duckduckgo.savedsites.impl.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FavouritesNewTabSettingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: FavouritesNewTabSettingsViewModel
    private val setting: NewTabFavouritesSectionSetting = mock()
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
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return true
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.enabled)
            }
        }
    }

    @Test
    fun whenViewCreatedAndSettingDisabledThenViewStateUpdated() = runTest {
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return false
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.enabled)
            }
        }
    }

    @Test
    fun whenSettingEnabledThenPixelFired() = runTest {
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return false
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onSettingEnabled(true)
        verify(pixels).fire(SavedSitesPixelName.FAVOURITES_SECTION_TOGGLED_ON)
    }

    @Test
    fun whenSettingDisabledThenPixelFired() = runTest {
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return false
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onSettingEnabled(false)
        verify(pixels).fire(SavedSitesPixelName.FAVOURITES_SECTION_TOGGLED_OFF)
    }
}
