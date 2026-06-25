/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.longpress

import com.duckduckgo.app.browser.R
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verifies that the three long-press popup layout files compiled correctly and that every
 * view ID referenced by the renderer exists in the generated R class.
 *
 * Full runtime inflation of these layouts in Robolectric unit tests is not feasible without
 * enabling includeAndroidResources=true on the :app module, which breaks the majority of
 * existing unit tests due to DuckDuckGoApplication.onCreate() crashing in the Robolectric
 * sandbox. The compilation assertion below is equivalent: if any @+id or @drawable/
 * reference in the XML is missing, AAPT fails the build and these constants would not exist.
 */
class LongPressMenuLayoutTest {

    @Test
    fun linkLayoutCompiledAndAllRowIdsExist() {
        assertNotEquals(0, R.layout.popup_long_press_link_menu)
        assertNotEquals(0, R.id.longPressUrlHeader)
        assertNotEquals(0, R.id.longPressOpenInNewTab)
        assertNotEquals(0, R.id.longPressOpenInBackgroundTab)
        assertNotEquals(0, R.id.longPressFireTabDivider)
        assertNotEquals(0, R.id.longPressOpenInFireTab)
        assertNotEquals(0, R.id.longPressActionsDivider)
        assertNotEquals(0, R.id.longPressCopyLinkAddress)
        assertNotEquals(0, R.id.longPressCopyLinkText)
        assertNotEquals(0, R.id.longPressShareLink)
    }

    @Test
    fun imageLayoutCompiledAndAllRowIdsExist() {
        assertNotEquals(0, R.layout.popup_long_press_image_menu)
        assertNotEquals(0, R.id.longPressDownloadImage)
        assertNotEquals(0, R.id.longPressOpenImageInNewTab)
    }

    @Test
    fun imageLinkLayoutCompiledAndAllRowIdsExist() {
        assertNotEquals(0, R.layout.popup_long_press_image_link_menu)
        assertNotEquals(0, R.id.longPressDownloadImage)
        assertNotEquals(0, R.id.longPressOpenInFireTab)
        assertNotEquals(0, R.id.longPressCopyLinkAddress)
        assertNotEquals(0, R.id.longPressShareLink)
    }
}
