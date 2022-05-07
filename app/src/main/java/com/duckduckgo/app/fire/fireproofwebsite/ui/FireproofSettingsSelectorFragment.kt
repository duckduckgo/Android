/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogRadioGroupSelectorFragmentBinding
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper.AutomaticFireproofSetting
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FireproofSettingsSelectorFragment : DialogFragment() {

    private lateinit var binding: DialogRadioGroupSelectorFragmentBinding

    interface Listener {
        fun onAutomaticFireproofSettingSelected(selectedSetting: AutomaticFireproofSetting)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: AutomaticFireproofSetting =
            arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as AutomaticFireproofSetting? ?: AutomaticFireproofSetting.ASK_EVERY_TIME

        binding = DialogRadioGroupSelectorFragmentBinding.inflate(LayoutInflater.from(context))

        setRadioButtonOptions(binding)
        updateCurrentSelection(currentOption, binding.selectorRadioGroup)

        val alertBuilder = MaterialAlertDialogBuilder(requireActivity())
            .setView(binding.root)
            .setTitle(R.string.fireproofWebsiteSettingSelectionTitle)
            .setPositiveButton(R.string.dialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = binding.selectorRadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.selectorRadioButton2 -> AutomaticFireproofSetting.ALWAYS
                        R.id.selectorRadioButton3 -> AutomaticFireproofSetting.NEVER
                        else -> AutomaticFireproofSetting.ASK_EVERY_TIME
                    }
                    val listener = activity as Listener?
                    listener?.onAutomaticFireproofSettingSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun setRadioButtonOptions(binding: DialogRadioGroupSelectorFragmentBinding) {
        binding.selectorRadioButton1.setText(R.string.fireproofWebsiteSettingsSelectionDialogAskEveryTime)
        binding.selectorRadioButton2.setText(R.string.fireproofWebsiteSettingsSelectionDialogAlways)
        binding.selectorRadioButton3.setText(R.string.fireproofWebsiteSettingsSelectionDialogNever)
    }

    private fun updateCurrentSelection(
        currentOption: AutomaticFireproofSetting,
        radioGroup: RadioGroup
    ) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun AutomaticFireproofSetting.radioButtonId(): Int {
        return when (this) {
            AutomaticFireproofSetting.ASK_EVERY_TIME -> R.id.selectorRadioButton1
            AutomaticFireproofSetting.ALWAYS -> R.id.selectorRadioButton2
            AutomaticFireproofSetting.NEVER -> R.id.selectorRadioButton3
        }
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(automaticFireproofSetting: AutomaticFireproofSetting): FireproofSettingsSelectorFragment =
            FireproofSettingsSelectorFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(DEFAULT_OPTION_EXTRA, automaticFireproofSetting)
                }
            }
    }
}
