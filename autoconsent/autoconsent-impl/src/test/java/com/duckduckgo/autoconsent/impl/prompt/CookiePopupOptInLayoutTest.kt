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

package com.duckduckgo.autoconsent.impl.prompt

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autoconsent.impl.databinding.ActivityCookiePopupOptInBinding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The option row background resolves ?attr/onboarding* colors, which only exist under the onboarding
 * theme overlay applied by the layout root. Inflating here is what catches a broken attribute reference.
 */
@RunWith(AndroidJUnit4::class)
class CookiePopupOptInLayoutTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)

    @Test
    fun whenInflatedThenOptionRowsResolveSelectedAndUnselectedBackgrounds() {
        val binding = ActivityCookiePopupOptInBinding.inflate(LayoutInflater.from(context))

        val maxOption = binding.cookiePopupOptInMaxOption.root
        assertNotNull(maxOption.background)

        maxOption.isSelected = true
        assertTrue(maxOption.isSelected)
        assertNotNull(maxOption.background.current)

        maxOption.isSelected = false
        assertFalse(maxOption.isSelected)
        assertNotNull(maxOption.background.current)
    }
}
