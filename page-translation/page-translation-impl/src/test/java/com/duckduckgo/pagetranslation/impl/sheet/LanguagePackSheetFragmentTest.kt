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
package com.duckduckgo.pagetranslation.impl.sheet

import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LanguagePackSheetFragmentTest {

    private val pair = LanguagePair("es", "en")
    private val activity = Robolectric.buildActivity(SheetTestActivity::class.java).setup().get()

    private fun showSheet(sizeBytes: Long?): View {
        val sheet = LanguagePackSheetFragment.newInstance(pair, sizeBytes)
        sheet.show(activity.supportFragmentManager, LanguagePackSheetFragment.TAG)
        activity.supportFragmentManager.executePendingTransactions()
        return sheet.requireView()
    }

    @Test
    fun whenSizeIsUnknownThenSizeLineIsHidden() {
        val view = showSheet(sizeBytes = null)

        assertEquals(View.GONE, view.findViewById<View>(R.id.languagePackSheetSize).visibility)
    }

    @Test
    fun whenSizeIsKnownThenSizeLineShowsFormattedSize() {
        val view = showSheet(sizeBytes = 23_000_000L)

        val size = view.findViewById<TextView>(R.id.languagePackSheetSize)
        assertEquals(View.VISIBLE, size.visibility)
        assertTrue(size.text.toString(), size.text.contains("23"))
        assertTrue(size.text.toString(), size.text.contains("MB"))
    }

    @Test
    fun whenSheetShownThenTitleNamesTheSourceLanguage() {
        val view = showSheet(sizeBytes = null)

        val title = view.findViewById<TextView>(R.id.languagePackSheetTitle).text.toString()
        assertTrue(title, title.contains("Spanish"))
    }

    @Test
    fun whenSheetIsExpandedThenTopCornersStayRounded() {
        val sheet = LanguagePackSheetFragment.newInstance(pair, sizeBytes = null)
        sheet.show(activity.supportFragmentManager, LanguagePackSheetFragment.TAG)
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = sheet.dialog as BottomSheetDialog

        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val background = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!.background as MaterialShapeDrawable
        shadowOf(Looper.getMainLooper()).idle()
        val bounds = RectF(0f, 0f, 1000f, 1000f)
        // Material animates this to 0 when an expanded sheet would otherwise lose its corners.
        assertEquals(1f, background.interpolation, 0f)
        assertTrue(background.shapeAppearanceModel.topLeftCornerSize.getCornerSize(bounds) > 0f)
        assertTrue(background.shapeAppearanceModel.topRightCornerSize.getCornerSize(bounds) > 0f)
    }
}
