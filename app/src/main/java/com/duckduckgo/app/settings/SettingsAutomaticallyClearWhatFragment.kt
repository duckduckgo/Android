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
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhatOption.*

class SettingsAutomaticallyClearWhatFragment : DialogFragment() {

    interface Listener {
        fun onAutomaticallyClearWhatOptionSelected(clearWhatSetting: ClearWhatOption)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: ClearWhatOption = arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as ClearWhatOption? ?: CLEAR_NONE

        val rootView = View.inflate(activity, R.layout.settings_automatically_clear_what_fragment, null)
        updateCurrentSelect(currentOption, rootView.findViewById(R.id.settingsClearWhatGroup))

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.settingsAutomaticallyClearWhatDialogTitle)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = it.findViewById(R.id.settingsClearWhatGroup) as RadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.settingTabsOnly -> CLEAR_TABS_ONLY
                        R.id.settingTabsAndData -> CLEAR_TABS_AND_DATA
                        else -> CLEAR_NONE
                    }
                    val listener = activity as Listener?
                    listener?.onAutomaticallyClearWhatOptionSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(currentOption: ClearWhatOption, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun ClearWhatOption.radioButtonId(): Int {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> R.id.settingNone
            ClearWhatOption.CLEAR_TABS_ONLY -> R.id.settingTabsOnly
            ClearWhatOption.CLEAR_TABS_AND_DATA -> R.id.settingTabsAndData
        }
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(clearWhatSetting: ClearWhatOption?): SettingsAutomaticallyClearWhatFragment {
            val fragment = SettingsAutomaticallyClearWhatFragment()

            fragment.arguments = Bundle().also {
                it.putSerializable(DEFAULT_OPTION_EXTRA, clearWhatSetting)

            }
            return fragment
        }
    }

}
