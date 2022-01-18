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
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.ClearWhenOption.*
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class SettingsAutomaticallyClearWhenFragment : DialogFragment() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    interface Listener {
        fun onAutomaticallyClearWhenOptionSelected(clearWhenSetting: ClearWhenOption)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: ClearWhenOption = arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as ClearWhenOption? ?: APP_EXIT_ONLY

        val rootView = View.inflate(activity, R.layout.settings_automatically_clear_when_fragment, null)

        if (appBuildConfig.isDebug) {
            showDebugOnlyOption(rootView)
        }

        updateCurrentSelect(currentOption, rootView.findViewById(R.id.settingsClearWhenGroup))

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.settingsAutomaticallyClearWhenDialogTitle)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = it.findViewById(R.id.settingsClearWhenGroup) as RadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.settingInactive5Mins -> APP_EXIT_OR_5_MINS
                        R.id.settingInactive15Mins -> APP_EXIT_OR_15_MINS
                        R.id.settingInactive30Mins -> APP_EXIT_OR_30_MINS
                        R.id.settingInactive60Mins -> APP_EXIT_OR_60_MINS
                        R.id.settingInactive5Seconds -> APP_EXIT_OR_5_SECONDS
                        else -> APP_EXIT_ONLY
                    }
                    val listener = activity as Listener?
                    listener?.onAutomaticallyClearWhenOptionSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(
        currentOption: ClearWhenOption,
        radioGroup: RadioGroup
    ) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    private fun showDebugOnlyOption(rootView: View) {
        val debugOption: View = rootView.findViewById(R.id.settingInactive5Seconds) ?: return
        debugOption.show()
    }

    @IdRes
    private fun ClearWhenOption.radioButtonId(): Int {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> R.id.settingAppExitOnly
            ClearWhenOption.APP_EXIT_OR_5_MINS -> R.id.settingInactive5Mins
            ClearWhenOption.APP_EXIT_OR_15_MINS -> R.id.settingInactive15Mins
            ClearWhenOption.APP_EXIT_OR_30_MINS -> R.id.settingInactive30Mins
            ClearWhenOption.APP_EXIT_OR_60_MINS -> R.id.settingInactive60Mins
            ClearWhenOption.APP_EXIT_OR_5_SECONDS -> R.id.settingInactive5Seconds
        }
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
}
