/*
 * Copyright (c) 2021 DuckDuckGo
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
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogRadioGroupSelectorFragmentBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SettingsAppLinksSelectorFragment : DialogFragment() {

    private val binding by viewBinding(DialogRadioGroupSelectorFragmentBinding::inflate)

    interface Listener {
        fun onAppLinkSettingSelected(selectedSetting: AppLinkSettingType)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: AppLinkSettingType =
            arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as AppLinkSettingType? ?: AppLinkSettingType.ASK_EVERYTIME

        updateCurrentSelection(currentOption, binding.selectorRadioGroup)

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .setTitle(R.string.settingsTitleAppLinksDialog)
            .setPositiveButton(R.string.dialogSave) { _, _ ->
                dialog?.let {
                    val selectedOption = when (binding.selectorRadioGroup.checkedRadioButtonId) {
                        R.id.selectorRadioButton1 -> AppLinkSettingType.ASK_EVERYTIME
                        R.id.selectorRadioButton2 -> AppLinkSettingType.ALWAYS
                        R.id.selectorRadioButton3 -> AppLinkSettingType.NEVER
                        else -> AppLinkSettingType.ASK_EVERYTIME
                    }
                    val listener = activity as Listener?
                    listener?.onAppLinkSettingSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelection(
        currentOption: AppLinkSettingType,
        radioGroup: RadioGroup
    ) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun AppLinkSettingType.radioButtonId(): Int {
        return when (this) {
            AppLinkSettingType.ASK_EVERYTIME -> R.id.selectorRadioButton1
            AppLinkSettingType.ALWAYS -> R.id.selectorRadioButton2
            AppLinkSettingType.NEVER -> R.id.selectorRadioButton3
        }
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(appLinkSettingType: AppLinkSettingType?): SettingsAppLinksSelectorFragment {
            val fragment = SettingsAppLinksSelectorFragment()

            fragment.arguments = Bundle().also {
                it.putSerializable(DEFAULT_OPTION_EXTRA, appLinkSettingType)
            }
            return fragment
        }
    }
}
