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

import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class RebrandAreaOverlayTest {

    private val areas = mapOf(
        R.attr.daxRebrandBadges to R.style.ThemeOverlay_Rebrand_Badges,
        R.attr.daxRebrandInfoPanels to R.style.ThemeOverlay_Rebrand_InfoPanels,
    )

    @Test
    fun whenNoAreaOverlayAppliedThenEveryAreaIsDisabled() {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { themeId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeId)
            areas.keys.forEach { assertFalse(context.isRebrandAreaEnabled(it)) }
        }
    }

    @Test
    fun whenAreaOverlayAppliedThenOnlyThatAreaIsEnabled() {
        areas.forEach { (attr, overlay) ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuckDuckGo_Light)
            context.theme.applyStyle(overlay, true)
            areas.keys.forEach { other -> assertEquals(other == attr, context.isRebrandAreaEnabled(other)) }
        }
    }
}
