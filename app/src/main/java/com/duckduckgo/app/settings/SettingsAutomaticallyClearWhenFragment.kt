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
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption.APP_EXIT_ONLY


class SettingsAutomaticallyClearWhenFragment : DialogFragment() {

    interface Listener {
        fun onAutomaticallyClearWhenOptionSelected(clearWhenSetting: ClearWhenOption)
    }

    var listener: Listener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: ClearWhenOption = arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as ClearWhenOption? ?: APP_EXIT_ONLY

        val rootView = View.inflate(activity, R.layout.settings_automatically_clear_when_fragment, null)
        val radioGroup: RadioGroup = rootView.findViewById(R.id.settingsClearWhenGroup)
        updateCurrentSelect(currentOption, radioGroup)

        val alertBuilder = AlertDialog.Builder(activity!!)
            .setView(rootView)
            .setTitle(R.string.settingsAutomaticallyClearWhat)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave) { _, _ ->
                val selectedOption = when (radioGroup.checkedRadioButtonId) {
                    R.id.settingInactive5Mins -> ClearWhenOption.APP_EXIT_OR_5_MINS
                    R.id.settingInactive15Mins -> ClearWhenOption.APP_EXIT_OR_15_MINS
                    R.id.settingInactive30Mins -> ClearWhenOption.APP_EXIT_OR_30_MINS
                    R.id.settingInactive60Mins -> ClearWhenOption.APP_EXIT_OR_60_MINS
                    else -> APP_EXIT_ONLY
                }
                listener?.onAutomaticallyClearWhenOptionSelected(selectedOption)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(currentOption: ClearWhenOption, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId
        radioGroup.check(selectedId)
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(clearWhenSetting: ClearWhenOption?): SettingsAutomaticallyClearWhenFragment {
            val fragment = SettingsAutomaticallyClearWhenFragment()

            fragment.arguments = Bundle().also {
                it.putSerializable(DEFAULT_OPTION_EXTRA, clearWhenSetting)

            }
            return fragment
        }
    }

    enum class ClearWhenOption constructor(@IdRes val radioButtonId: Int, @StringRes val nameStringRes: Int) {
        APP_EXIT_ONLY(R.id.settingAppExitOnly, R.string.settingsAutomaticallyClearWhenAppExitOnly),
        APP_EXIT_OR_5_MINS(R.id.settingInactive5Mins, R.string.settingsAutomaticallyClearWhenAppExit5Mins),
        APP_EXIT_OR_15_MINS(R.id.settingInactive15Mins, R.string.settingsAutomaticallyClearWhenAppExit15Mins),
        APP_EXIT_OR_30_MINS(R.id.settingInactive30Mins, R.string.settingsAutomaticallyClearWhenAppExit30Mins),
        APP_EXIT_OR_60_MINS(R.id.settingInactive60Mins, R.string.settingsAutomaticallyClearWhenAppExit60Mins),
    }

}