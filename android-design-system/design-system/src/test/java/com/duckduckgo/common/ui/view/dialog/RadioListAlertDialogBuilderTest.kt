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

package com.duckduckgo.common.ui.view.dialog

import android.content.Context
import android.widget.RadioGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class RadioListAlertDialogBuilderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_DuckDuckGo_Light)
    }

    @Test
    fun whenOptionsAreAddedInMultipleCallsThenSelectionIsOffsetByPreviouslyAddedOptions() {
        RadioListAlertDialogBuilder(context)
            .setTitle(R.string.dialogConfirmTitle)
            .setOptions(listOf(RadioListOption(R.string.dialogAddTitle), RadioListOption(R.string.dialogEditTitle)))
            .setOptions(listOf(RadioListOption(R.string.dialogSave, isSelected = true)))
            .setPositiveButton(R.string.dialogSave)
            .setNegativeButton(R.string.dialogAddTitle)
            .show()

        val radioGroup = ShadowDialog.getLatestDialog().findViewById<RadioGroup>(R.id.radioListDialogRadioGroup)

        assertEquals(3, radioGroup.childCount)
        assertEquals(3, radioGroup.checkedRadioButtonId)
    }
}
