/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.pixels

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface NewTabPixels {
    // Engagement pixels https://app.asana.com/0/72649045549333/1207667088727866/f
    fun fireCustomizePagePressedPixel()
    fun fireShortcutPressed(shortcutName: String)
    fun fireShortcutAdded(shortcutName: String)
    fun fireShortcutRemoved(shortcutName: String)
    fun fireShortcutSectionToggled(enabled: Boolean)
    fun fireSectionReordered()
    fun fireNewTabDisplayed()
}

@ContributesBinding(AppScope::class)
class RealNewTabPixels @Inject constructor(
    private val pixel: Pixel,
    private val sections: ActivePluginPoint<NewTabPageSectionPlugin>,
    private val savedSitesRepository: SavedSitesRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : NewTabPixels {

    override fun fireCustomizePagePressedPixel() {
        pixel.fire(NewTabPixelNames.CUSTOMIZE_PAGE_PRESSED)
    }

    override fun fireShortcutPressed(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_PRESSED.pixelName + shortcutName)
    }

    override fun fireShortcutAdded(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_ADDED.pixelName + shortcutName)
    }

    override fun fireShortcutRemoved(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_REMOVED.pixelName + shortcutName)
    }

    override fun fireShortcutSectionToggled(enabled: Boolean) {
        if (enabled) {
            pixel.fire(NewTabPixelNames.SHORTCUT_SECTION_TOGGLED_ON)
        } else {
            pixel.fire(NewTabPixelNames.SHORTCUT_SECTION_TOGGLED_OFF)
        }
    }

    override fun fireSectionReordered() {
        pixel.fire(NewTabPixelNames.SECTION_REARRANGED)
    }

    override fun fireNewTabDisplayed() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val paramsMap = mutableMapOf<String, String>().apply {
                val allSections = sections.getPlugins()
                val favoriteSection = getSectionParameterValue(allSections.firstOrNull { it.name == NewTabPageSection.FAVOURITES.name })
                put(NewTabPixelParameters.FAVORITES, favoriteSection)
                val shortcutsSection = getSectionParameterValue(allSections.firstOrNull { it.name == NewTabPageSection.SHORTCUTS.name })
                put(NewTabPixelParameters.SHORTCUTS, shortcutsSection)
                val appTPSection = getSectionParameterValue(allSections.firstOrNull { it.name == NewTabPageSection.APP_TRACKING_PROTECTION.name })
                put(NewTabPixelParameters.APP_TRACKING_PROTECTION, appTPSection)
                put(NewTabPixelParameters.FAVORITES_COUNT, getFavoritesParameterValue())
            }
            pixel.fire(NewTabPixelNames.NEW_TAB_DISPLAYED)
            pixel.fire(pixel = NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = Daily(), parameters = paramsMap)
        }
    }

    private suspend fun getSectionParameterValue(sectionPlugin: NewTabPageSectionPlugin?): String {
        return if (sectionPlugin != null) {
            if (sectionPlugin.isUserEnabled()) {
                NewTabPixelValues.SECTION_ENABLED
            } else {
                NewTabPixelValues.SECTION_DISABLED
            }
        } else {
            NewTabPixelValues.SECTION_DISABLED
        }
    }

    private fun getFavoritesParameterValue(): String {
        val favorites = savedSitesRepository.favoritesCount()
        if (favorites < 2) {
            return favorites.toString()
        }
        if (favorites < 4) {
            return NewTabPixelValues.FAVORITES_2_3
        }
        if (favorites < 6) {
            return NewTabPixelValues.FAVORITES_4_5
        }
        if (favorites < 11) {
            return NewTabPixelValues.FAVORITES_6_10
        }
        if (favorites < 16) {
            return NewTabPixelValues.FAVORITES_11_15
        }
        if (favorites < 26) {
            return NewTabPixelValues.FAVORITES_16_25
        }
        return NewTabPixelValues.FAVORITES_25
    }
}
