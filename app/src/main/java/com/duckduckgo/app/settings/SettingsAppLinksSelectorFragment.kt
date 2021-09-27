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
import android.view.View
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R

class SettingsAppLinksSelectorFragment : DialogFragment() {

    interface Listener {
        fun onAppLinkSettingSelected(selectedSetting: AppLinkSettingType)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: AppLinkSettingType =
            arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as AppLinkSettingType? ?: AppLinkSettingType.ASK_EVERYTIME

        val rootView =
            View.inflate(activity, R.layout.settings_app_links_selector_fragment, null)

        updateCurrentSelection(currentOption, rootView.findViewById(R.id.appLinksSelectorGroup))

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.settingsTitleAppLinksDialog)
            .setPositiveButton(R.string.dialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = it.findViewById(R.id.appLinksSelectorGroup) as RadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.appLinksSelectorAskEveryTime -> AppLinkSettingType.ASK_EVERYTIME
                        R.id.appLinksSelectorAlways -> AppLinkSettingType.ALWAYS
                        R.id.appLinksSelectorNever -> AppLinkSettingType.NEVER
                        else -> AppLinkSettingType.ASK_EVERYTIME
                    }
                    val listener = activity as Listener?
                    listener?.onAppLinkSettingSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelection(currentOption: AppLinkSettingType, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun AppLinkSettingType.radioButtonId(): Int {
        return when (this) {
            AppLinkSettingType.ASK_EVERYTIME -> R.id.appLinksSelectorAskEveryTime
            AppLinkSettingType.ALWAYS -> R.id.appLinksSelectorAlways
            AppLinkSettingType.NEVER -> R.id.appLinksSelectorNever
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
