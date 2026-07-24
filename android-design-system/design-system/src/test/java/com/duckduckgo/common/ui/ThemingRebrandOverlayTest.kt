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

package com.duckduckgo.common.ui

import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemingRebrandOverlayTest {

    private fun resolveDaxButtonPrimary(activity: AppCompatActivity): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(R.attr.daxButtonPrimary, value, true)
        return value.resourceId
    }

    @Test
    fun whenApplyThemeWithBrandDesignUpdateThenRebrandStyleResolves() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenApplyThemeWithoutBrandDesignUpdateThenLegacyStyleResolves() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        activity.applyTheme(DuckDuckGoTheme.LIGHT)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, resolveDaxButtonPrimary(activity))
    }
}
