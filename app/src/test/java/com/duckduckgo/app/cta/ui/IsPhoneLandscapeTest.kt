/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.cta.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IsPhoneLandscapeTest {

    @Test
    fun whenPhoneLandscapeThenReturnsTrue() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            smallestScreenWidthDp = 360,
        )
        assertTrue(view.isPhoneLandscape())
    }

    @Test
    fun whenPhonePortraitThenReturnsFalse() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_PORTRAIT,
            smallestScreenWidthDp = 360,
        )
        assertFalse(view.isPhoneLandscape())
    }

    @Test
    fun whenTabletLandscapeThenReturnsFalse() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            smallestScreenWidthDp = 720,
        )
        assertFalse(view.isPhoneLandscape())
    }

    @Test
    fun whenTabletPortraitThenReturnsFalse() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_PORTRAIT,
            smallestScreenWidthDp = 720,
        )
        assertFalse(view.isPhoneLandscape())
    }

    @Test
    fun whenSmallestScreenWidthIs600ThenIsTabletReturnsTrue() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_PORTRAIT,
            smallestScreenWidthDp = 600,
        )
        assertTrue(view.isTablet())
    }

    @Test
    fun whenSmallestScreenWidthIs599ThenIsTabletReturnsFalse() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_PORTRAIT,
            smallestScreenWidthDp = 599,
        )
        assertFalse(view.isTablet())
    }

    @Test
    fun whenSmallestScreenWidthIsLargeTabletThenIsTabletReturnsTrue() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            smallestScreenWidthDp = 800,
        )
        assertTrue(view.isTablet())
    }

    @Test
    fun whenSmallestScreenWidthIsTypicalPhoneThenIsTabletReturnsFalse() {
        val view = viewFor(
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            smallestScreenWidthDp = 360,
        )
        assertFalse(view.isTablet())
    }

    private fun viewFor(
        orientation: Int,
        smallestScreenWidthDp: Int,
    ): View {
        val view: View = mock()
        val context: Context = mock()
        val resources: Resources = mock()
        val configuration = Configuration().apply {
            this.orientation = orientation
            this.smallestScreenWidthDp = smallestScreenWidthDp
        }
        whenever(view.context).thenReturn(context)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        return view
    }
}
