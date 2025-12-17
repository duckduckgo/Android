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

package com.duckduckgo.duckchat.impl.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updatePadding
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.R as MaterialR

class InputBottomSheetFragment : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Set up BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        // WindowPreferencesManager(requireContext()).applyEdgeToEdgePreference(bottomSheetDialog.getWindow())

        bottomSheetDialog.window?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.decorView) { view, insets ->
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                val extraMargin = (imeBottom).coerceAtLeast(0)

                view.updatePadding(bottom = extraMargin)
                insets
            }
            ViewCompat.requestApplyInsets(it.decorView)
        }

        bottomSheetDialog.setContentView(R.layout.cat_bottomsheet_unscrollable_content)
        val bottomSheetInternal = bottomSheetDialog.findViewById<View?>(MaterialR.id.design_bottom_sheet)

        // BottomSheetBehavior.from(bottomSheetInternal).setPeekHeight(400);
        val closeButton = bottomSheetDialog.findViewById<Button?>(R.id.close_icon)
        closeButton!!.setOnClickListener(View.OnClickListener { v: View? -> bottomSheetDialog.dismiss() })

        val bottomSheetContent = bottomSheetInternal!!.findViewById<View?>(R.id.bottom_drawer_3)

        // ViewUtils.doOnApplyWindowInsets(
        //     bottomSheetContent,
        //     ViewUtils.OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat?, initialPadding: RelativePadding? ->
        //         // Add the inset in the inner NestedScrollView instead to make the edge-to-edge behavior
        //         // consistent - i.e., the extra padding will only show at the bottom of all content, i.e.,
        //         // only when you can no longer scroll down to show more content.
        //         bottomSheetContent.setPaddingRelative(
        //             initialPadding!!.start,
        //             initialPadding.top,
        //             initialPadding.end,
        //             initialPadding.bottom + insets!!.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
        //         )
        //         insets
        //     },
        // )
        return bottomSheetDialog
    }
}
