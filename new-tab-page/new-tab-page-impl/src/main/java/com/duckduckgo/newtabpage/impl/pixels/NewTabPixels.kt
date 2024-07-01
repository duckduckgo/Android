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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NewTabPixels {
    // Engagement pixels https://app.asana.com/0/72649045549333/1207667088727866/f
    fun fireWelcomeMessageShownPixel()
    fun fireWelcomeMessageDismissedPixel()
    fun fireCustomizePagePressedPixel()
    fun fireShortcutPressed(shortcutName: String)
    fun fireShortcutAdded(shortcutName: String)
    fun fireShortcutRemoved(shortcutName: String)
    fun fireShortcutSectionToggled(enabled: Boolean)
    fun fireSectionReordered()
    fun fireNewTabDisplayed()
    fun fireEndOnboarding()

    // Experiment pixels https://app.asana.com/0/72649045549333/1207667088727867/f
}

@ContributesBinding(AppScope::class)
class RealNewTabPixels @Inject constructor(
    private val pixel: Pixel,
) : NewTabPixels {
    override fun fireWelcomeMessageShownPixel() {
        pixel.fire(NewTabPixelNames.WELCOME_MESSAGE_SHOWN)
    }

    override fun fireWelcomeMessageDismissedPixel() {
        pixel.fire(NewTabPixelNames.WELCOME_MESSAGE_DISMISSED)
    }

    override fun fireCustomizePagePressedPixel() {
        pixel.fire(NewTabPixelNames.CUSTOMIZE_PAGE_PRESSED)
    }

    override fun fireShortcutPressed(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_PRESSED.name + shortcutName)
    }

    override fun fireShortcutAdded(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_ADDED.name + shortcutName)
    }

    override fun fireShortcutRemoved(shortcutName: String) {
        pixel.fire(NewTabPixelNames.SHORTCUT_REMOVED.name + shortcutName)
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
        pixel.fire(NewTabPixelNames.NEW_TAB_DISPLAYED)
        pixel.fire(NewTabPixelNames.NEW_TAB_DISPLAYED_UNIQUE, type = DAILY)
    }

    override fun fireEndOnboarding() {
        pixel.fire(NewTabPixelNames.END_ONBOARDING_DISMISSED)
    }
}
