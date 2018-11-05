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
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.RadioGroup
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption.*
import timber.log.Timber


class SettingsAutomaticallyClearWhatFragment : DialogFragment() {

    interface Listener {
        fun onAutomaticallyClearWhatOptionSelected(clearWhatSetting: ClearWhatOption)
    }

    var listener: Listener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: ClearWhatOption = arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as ClearWhatOption? ?: CLEAR_NONE

        val rootView = View.inflate(activity, R.layout.settings_automatically_clear_what_fragment, null)
        val radioGroup: RadioGroup = rootView.findViewById(R.id.settingsClearWhenGroup)
        updateCurrentSelect(currentOption, radioGroup)

        val alertBuilder = AlertDialog.Builder(activity!!)
            .setView(rootView)
            .setTitle(R.string.settingsAutomaticallyClearWhat)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave) { _, _ ->
                val selectedOption = when(radioGroup.checkedRadioButtonId) {
                    R.id.settingTabsOnly -> CLEAR_TABS_ONLY
                    R.id.settingTabsAndData -> CLEAR_TABS_AND_DATA
                    else -> CLEAR_NONE
                }
                Timber.i("User selected option: $selectedOption")
                listener?.onAutomaticallyClearWhatOptionSelected(selectedOption)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                Timber.i("Negative button")
            }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(currentOption: ClearWhatOption, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId
        radioGroup.check(selectedId)
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

    enum class ClearWhatOption constructor(@IdRes val radioButtonId: Int, @StringRes val nameStringRes: Int) {
        CLEAR_NONE(R.id.settingNone, R.string.settingsAutomaticallyClearWhatOptionNone),
        CLEAR_TABS_ONLY(R.id.settingTabsOnly, R.string.settingsAutomaticallyClearWhatOptionTabs),
        CLEAR_TABS_AND_DATA(R.id.settingTabsAndData, R.string.settingsAutomaticallyClearWhatOptionTabsAndData)
    }

    enum class ClearWhenOption constructor(@IdRes val radioButtonId: Int, @StringRes val nameStringRes: Int) {
        APP_EXIT_ONLY(R.id.settingNone, R.string.settingsAutomaticallyClearWhenAppExitOnly),
        APP_EXIT_OR_5_MINS(R.id.settingNone, R.string.settingsAutomaticallyClearWhenAppExit5Mins),
        APP_EXIT_OR_15_MINS(R.id.settingNone, R.string.settingsAutomaticallyClearWhenAppExit15Mins),
        APP_EXIT_OR_30_MINS(R.id.settingNone, R.string.settingsAutomaticallyClearWhenAppExit30Mins),
        APP_EXIT_OR_60_MINS(R.id.settingNone, R.string.settingsAutomaticallyClearWhenAppExit60Mins),
    }

}