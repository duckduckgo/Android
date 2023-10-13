/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.settings.geoswitching

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.TextType
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Typography
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Typography.H4
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.DialogGeoswitchingCityBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import logcat.logcat

@InjectWith(FragmentScope::class)
class NetpGeoswitchingCityChoiceDialogFragment private constructor() : BottomSheetDialogFragment() {
    override fun getTheme(): Int = com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialogCollapsed
    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return DialogGeoswitchingCityBinding.inflate(inflater, container, false).apply {
            configureViews(this)
        }.root
    }

    private fun configureViews(dialogGeoswitchingCityBinding: DialogGeoswitchingCityBinding) {
        val cityName = requireArguments().getString(ARGUMENT_COUNTRY_NAME)
        val cities = requireArguments().getStringArrayList(ARGUMENT_CITIES)

        dialogGeoswitchingCityBinding.countryName.text = cityName
        dialogGeoswitchingCityBinding.recommendedCityItem.style()

        cities?.forEachIndexed { index, city ->
            val radioButton = RadioButton(dialogGeoswitchingCityBinding.cityRadioGroup.context, null)
            radioButton.style()
            radioButton.id = index + 1
            val params = RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            radioButton.layoutParams = params
            radioButton.text = city
            dialogGeoswitchingCityBinding.cityRadioGroup.addView(radioButton)
        }

        dialogGeoswitchingCityBinding.cityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.recommended_city_item) {
                logcat { "KLDIMSUM: selected nearest available" }
            } else {
                logcat { "KLDIMSUM: selected ${cities?.get(checkedId - 1)}" }
            }
        }
    }

    private fun RadioButton.style() {
        setTextAppearance(Typography.getTextAppearanceStyle(H4))
        setTextColor(ContextCompat.getColorStateList(requireContext(), TextType.getTextColorStateList(TextType.Primary)))
    }

    companion object {
        private const val ARGUMENT_COUNTRY_NAME = "countryname"
        private const val ARGUMENT_CITIES = "cities"
        fun instance(
            countryName: String,
            cities: ArrayList<String>
        ): NetpGeoswitchingCityChoiceDialogFragment {
            return NetpGeoswitchingCityChoiceDialogFragment().apply {
                arguments = Bundle().also {
                    it.putString(ARGUMENT_COUNTRY_NAME, countryName)
                    it.putStringArrayList(ARGUMENT_CITIES, cities)
                }
            }
        }
    }
}
