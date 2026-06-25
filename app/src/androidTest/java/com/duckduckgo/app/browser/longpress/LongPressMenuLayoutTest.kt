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

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongPressMenuLayoutTest {

    private val context = ContextThemeWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext,
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_App,
    )

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    @Test
    fun linkLayoutHasAllRows() {
        val view = inflater.inflate(R.layout.popup_long_press_link_menu, null)
        listOf(
            R.id.longPressUrlHeader,
            R.id.longPressOpenInNewTab,
            R.id.longPressOpenInBackgroundTab,
            R.id.longPressFireTabDivider,
            R.id.longPressOpenInFireTab,
            R.id.longPressActionsDivider,
            R.id.longPressCopyLinkAddress,
            R.id.longPressCopyLinkText,
            R.id.longPressShareLink,
        ).forEach { assertNotNull(view.findViewById<View>(it)) }
    }

    @Test
    fun imageLayoutHasAllRows() {
        val view = inflater.inflate(R.layout.popup_long_press_image_menu, null)
        listOf(
            R.id.longPressUrlHeader,
            R.id.longPressDownloadImage,
            R.id.longPressOpenImageInNewTab,
        ).forEach { assertNotNull(view.findViewById<View>(it)) }
    }

    @Test
    fun imageLinkLayoutHasAllRowsAndNoCopyLinkText() {
        val view = inflater.inflate(R.layout.popup_long_press_image_link_menu, null)
        listOf(
            R.id.longPressUrlHeader,
            R.id.longPressDownloadImage,
            R.id.longPressOpenImageInNewTab,
            R.id.longPressOpenInNewTab,
            R.id.longPressOpenInBackgroundTab,
            R.id.longPressFireTabDivider,
            R.id.longPressOpenInFireTab,
            R.id.longPressActionsDivider,
            R.id.longPressCopyLinkAddress,
            R.id.longPressShareLink,
        ).forEach { assertNotNull(view.findViewById<View>(it)) }
        assertNull(view.findViewById<View>(R.id.longPressCopyLinkText))
    }
}
