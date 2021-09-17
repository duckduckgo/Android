/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.*

class SettingsThemeSelectorFragment : DialogFragment() {

    interface Listener {
        fun onThemeSelected(selectedTheme: DuckDuckGoTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: DuckDuckGoTheme =
            arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as DuckDuckGoTheme? ?: LIGHT

        val rootView =
            View.inflate(activity, R.layout.settings_theme_selector_fragment, null)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            rootView.findViewById<RadioButton>(R.id.themeSelectorSystemDefault).visibility =
                View.VISIBLE
        }
        updateCurrentSelect(currentOption, rootView.findViewById(R.id.themeSelectorGroup))

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.settingsTheme)
            .setPositiveButton(R.string.settingsThemeDialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = it.findViewById(R.id.themeSelectorGroup) as RadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.themeSelectorLight -> LIGHT
                        R.id.themeSelectorDark -> DARK
                        R.id.themeSelectorSystemDefault -> SYSTEM_DEFAULT
                        else -> LIGHT
                    }
                    val listener = activity as Listener?
                    listener?.onThemeSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(currentOption: DuckDuckGoTheme, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun DuckDuckGoTheme.radioButtonId(): Int {
        return when (this) {
            LIGHT -> R.id.themeSelectorLight
            DARK -> R.id.themeSelectorDark
            SYSTEM_DEFAULT -> R.id.themeSelectorSystemDefault
        }
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(selectedFireAnimation: DuckDuckGoTheme?): SettingsThemeSelectorFragment {
            val fragment = SettingsThemeSelectorFragment()

            fragment.arguments = Bundle().also {
                it.putSerializable(DEFAULT_OPTION_EXTRA, selectedFireAnimation)
            }
            return fragment
        }
    }

}
