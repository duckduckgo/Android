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

package com.duckduckgo.app.sitepermissions.permissionsperwebsite

import android.app.Dialog
import android.os.Bundle
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogRadioGroupSelectorFragmentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PermissionsSettingsSelectorFragment : DialogFragment() {

    private lateinit var binding: DialogRadioGroupSelectorFragmentBinding

    interface Listener {
        fun onPermissionSettingSelected(websitePermissionSetting: WebsitePermissionSetting)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val permissionSelected = arguments?.getSerializable(PERMISSION_SELECTED_EXTRA) as WebsitePermissionSetting
        val currentOption: WebsitePermissionSettingType = permissionSelected.setting
        val domain = arguments?.getString(DOMAIN_EXTRA) ?: ""

        binding = DialogRadioGroupSelectorFragmentBinding.inflate(layoutInflater)

        setRadioButtonOptions(binding)
        updateCurrentSelection(currentOption, binding.selectorRadioGroup)
        val dialogTitle = String.format(getString(R.string.permissionsPerWebsiteSelectorDialogTitle), getString(permissionSelected.title), domain)

        val alertBuilder = MaterialAlertDialogBuilder(requireActivity())
            .setView(binding.root)
            .setTitle(dialogTitle)
            .setPositiveButton(R.string.dialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = binding.selectorRadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.selectorRadioButton2 -> WebsitePermissionSettingType.DENY
                        R.id.selectorRadioButton3 -> WebsitePermissionSettingType.ALLOW
                        else -> WebsitePermissionSettingType.ASK
                    }
                    val newPermissionSetting = WebsitePermissionSetting(permissionSelected.icon, permissionSelected.title, selectedOption)
                    val listener = activity as Listener?
                    listener?.onPermissionSettingSelected(newPermissionSetting)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun setRadioButtonOptions(binding: DialogRadioGroupSelectorFragmentBinding) {
        binding.selectorRadioButton1.setText(R.string.permissionsPerWebsiteAskSetting)
        binding.selectorRadioButton2.setText(R.string.permissionsPerWebsiteDenySetting)
        binding.selectorRadioButton3.setText(R.string.permissionsPerWebsiteAllowSetting)
    }

    private fun updateCurrentSelection(
        currentOption: WebsitePermissionSettingType,
        radioGroup: RadioGroup
    ) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun WebsitePermissionSettingType.radioButtonId(): Int {
        return when (this) {
            WebsitePermissionSettingType.ASK -> R.id.selectorRadioButton1
            WebsitePermissionSettingType.DENY -> R.id.selectorRadioButton2
            WebsitePermissionSettingType.ALLOW -> R.id.selectorRadioButton3
        }
    }

    companion object {

        private const val PERMISSION_SELECTED_EXTRA = "PERMISSION_SELECTED"
        private const val DOMAIN_EXTRA = "DOMAIN"

        fun create(websitePermissionSetting: WebsitePermissionSetting, domain: String): PermissionsSettingsSelectorFragment =
            PermissionsSettingsSelectorFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(PERMISSION_SELECTED_EXTRA, websitePermissionSetting)
                    putString(DOMAIN_EXTRA, domain)
                }
            }
    }
}
