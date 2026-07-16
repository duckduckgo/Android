package com.duckduckgo.newtabpage.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.BROWSER_MODE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.newtabpage.impl.disabledSectionPlugins
import com.duckduckgo.newtabpage.impl.enabledSectionsPlugins
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNewTabPixelsTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: RealNewTabPixels

    private val pixel: Pixel = mock()
    private val savedSitesRepository: SavedSitesRepository = mock()
    private val browserModeStateHolder: BrowserModeStateHolder = mock {
        on { currentMode } doReturn MutableStateFlow(BrowserMode.REGULAR)
    }

    @Before
    fun setup() {
        testee = RealNewTabPixels(
            pixel,
            enabledSectionsPlugins,
            savedSitesRepository,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            browserModeStateHolder,
        )
    }

    @Test
    fun whenFireCustomizePagePressedPixelThenPixelFired() {
        testee.fireCustomizePagePressedPixel()

        verify(pixel).fire(NewTabPixelNames.CUSTOMIZE_PAGE_PRESSED)
    }

    @Test
    fun whenFireShortcutPressedThenPixelFired() {
        testee.fireShortcutPressed("shortcut")

        verify(pixel).fire(NewTabPixelNames.SHORTCUT_PRESSED.pixelName + "shortcut")
    }

    @Test
    fun whenFireShortcutAddedThenPixelFired() {
        testee.fireShortcutAdded("shortcut")

        verify(pixel).fire(NewTabPixelNames.SHORTCUT_ADDED.pixelName + "shortcut")
    }

    @Test
    fun whenFireShortcutRemovedThenPixelFired() {
        testee.fireShortcutRemoved("shortcut")

        verify(pixel).fire(NewTabPixelNames.SHORTCUT_REMOVED.pixelName + "shortcut")
    }

    @Test
    fun whenFireShortcutSectionToggledEnabledThenPixelFired() {
        testee.fireShortcutSectionToggled(true)

        verify(pixel).fire(NewTabPixelNames.SHORTCUT_SECTION_TOGGLED_ON)
    }

    @Test
    fun whenFireShortcutSectionToggledDisabledThenPixelFired() {
        testee.fireShortcutSectionToggled(false)

        verify(pixel).fire(NewTabPixelNames.SHORTCUT_SECTION_TOGGLED_OFF)
    }

    @Test
    fun whenFireSectionReorderedThenPixelFired() {
        testee.fireSectionReordered()

        verify(pixel).fire(NewTabPixelNames.SECTION_REARRANGED)
    }

    @Test
    fun whenNewTabDisplayedAndNoFavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(0)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, "0")
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd1FavoriteThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(1)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, "1")
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd3FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(3)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_2_3)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd5FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(5)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_4_5)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd10FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(10)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_6_10)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd15FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(15)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_11_15)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd20FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(20)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_16_25)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAnd50FavoritesThenPixelFired() {
        whenever(savedSitesRepository.favoritesCount()).thenReturn(50)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "1")
            put(NewTabPixelParameters.SHORTCUTS, "1")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "1")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_25)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabDisplayedAndNoSectionsEnabledThenPixelFired() {
        testee = RealNewTabPixels(
            pixel,
            disabledSectionPlugins,
            savedSitesRepository,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            browserModeStateHolder,
        )

        whenever(savedSitesRepository.favoritesCount()).thenReturn(50)
        val paramsMap = mutableMapOf<String, String>().apply {
            put(NewTabPixelParameters.FAVORITES, "0")
            put(NewTabPixelParameters.SHORTCUTS, "0")
            put(NewTabPixelParameters.APP_TRACKING_PROTECTION, "0")
            put(NewTabPixelParameters.FAVORITES_COUNT, NewTabPixelValues.FAVORITES_25)
            put(BROWSER_MODE, "regular")
        }

        testee.fireNewTabDisplayed()

        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED, mapOf(BROWSER_MODE to "regular"))
        verify(pixel).fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED)
        verify(pixel).fire(NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY, type = Daily())
    }
}
