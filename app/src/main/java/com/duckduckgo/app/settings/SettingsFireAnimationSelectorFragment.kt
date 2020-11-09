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
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireAnimation.*

class SettingsFireAnimationSelectorFragment : DialogFragment() {

    interface Listener {
        fun onFireAnimationSelected(selectedFireAnimation: FireAnimation)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val currentOption: FireAnimation = arguments?.getSerializable(DEFAULT_OPTION_EXTRA) as FireAnimation? ?: HeroFire

        val rootView = View.inflate(activity, R.layout.settings_fire_animation_selector_fragment, null)

        updateCurrentSelect(currentOption, rootView.findViewById(R.id.fireAnimationSelectorGroup))

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(R.string.settingsSelectFireAnimationDialog)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave) { _, _ ->
                dialog?.let {
                    val radioGroup = it.findViewById(R.id.fireAnimationSelectorGroup) as RadioGroup
                    val selectedOption = when (radioGroup.checkedRadioButtonId) {
                        R.id.fireAnimationFire -> HeroFire
                        R.id.fireAnimationWater -> HeroWater
                        R.id.fireAnimationAbstract -> HeroAbstract
                        R.id.fireAnimationDisabled -> None
                        else -> HeroFire
                    }
                    val listener = activity as Listener?
                    listener?.onFireAnimationSelected(selectedOption)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    private fun updateCurrentSelect(currentOption: FireAnimation, radioGroup: RadioGroup) {
        val selectedId = currentOption.radioButtonId()
        radioGroup.check(selectedId)
    }

    @IdRes
    private fun FireAnimation.radioButtonId(): Int {
        return when (this) {
            HeroFire -> R.id.fireAnimationFire
            HeroWater -> R.id.fireAnimationWater
            HeroAbstract -> R.id.fireAnimationAbstract
            None -> R.id.fireAnimationDisabled
        }
    }

    companion object {

        private const val DEFAULT_OPTION_EXTRA = "DEFAULT_OPTION"

        fun create(selectedFireAnimation: FireAnimation?): SettingsFireAnimationSelectorFragment {
            val fragment = SettingsFireAnimationSelectorFragment()

            fragment.arguments = Bundle().also {
                it.putSerializable(DEFAULT_OPTION_EXTRA, selectedFireAnimation)

            }
            return fragment
        }
    }

}
