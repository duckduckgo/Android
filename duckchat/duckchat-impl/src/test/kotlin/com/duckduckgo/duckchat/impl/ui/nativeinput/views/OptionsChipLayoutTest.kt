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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OptionsChipLayoutTest {

    @Test
    fun whenToolChipShowsThenItIsNoTallerThanTheBottomRowButtons() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
        )
        // OptionsView inflates the chip into itself, a horizontal LinearLayout, so its layout params apply.
        val row = LinearLayout(context)
        val chip = LayoutInflater.from(context).inflate(R.layout.view_options_chip, row, false)
        row.addView(chip)

        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        row.measure(unspecified, unspecified)

        assertEquals(context.resources.getDimensionPixelSize(R.dimen.nativeInputButtonSize), chip.measuredHeight)
    }
}
