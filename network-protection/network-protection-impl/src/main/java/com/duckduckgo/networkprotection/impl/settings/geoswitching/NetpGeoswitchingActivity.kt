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

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpGeoswitchingBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemGeoswitchingCountryBinding
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoSwitchingViewModel.CountryItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoSwitchingViewModel.ViewState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetpGeoswitchingScreenNoParams::class)
class NetpGeoswitchingActivity : DuckDuckGoActivity() {
    private val binding: ActivityNetpGeoswitchingBinding by viewBinding()
    private val viewModel: NetpGeoSwitchingViewModel by bindViewModel()
    private var lastSelectedButton: CompoundButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeViewModel()
        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun onItemMenuClicked(
        country: String,
        cities: List<String>,
    ) {
        NetpGeoswitchingCityChoiceDialogFragment.instance(
            country,
            ArrayList(cities),
        ).show(supportFragmentManager, TAG_DIALOG_CITY_CHOICE)
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        bindRecommendedItem()

        if (viewState.items.isEmpty()) {
            binding.customListHeader.gone()
        } else {
            binding.customListHeader.show()
        }

        viewState.items.forEach {
            val itemBinding = ItemGeoswitchingCountryBinding.inflate(
                LayoutInflater.from(binding.geoswitchingList.context),
                binding.geoswitchingList,
                false,
            )

            it.bindLocationItem(itemBinding)
            binding.geoswitchingList.addView(itemBinding.root)
        }
    }

    private fun bindRecommendedItem() {
        with(binding.recommendedLocationItem) {
            // Sets initial state
            if (viewModel.hasNearestAvailableSelected()) {
                this.radioButton.isChecked = true
                lastSelectedButton = this.radioButton
            } else {
                this.radioButton.isChecked = false
            }

            this.radioButton.setOnCheckedChangeListener { view, isChecked ->
                if (isChecked && view != lastSelectedButton) {
                    lastSelectedButton?.isChecked = false
                    lastSelectedButton = view
                    viewModel.onNearestAvailableCountrySelected()
                }
            }
            // Automatically selects the country when the item is clicked
            this.setClickListener {
                this.radioButton.isChecked = true
            }
        }
    }

    private fun CountryItem.bindLocationItem(itemBinding: ItemGeoswitchingCountryBinding) {
        // Sets initial state
        if (viewModel.isLocationSelected(this.countryCode)) {
            itemBinding.root.radioButton.isChecked = true
            lastSelectedButton = itemBinding.root.radioButton
        } else {
            itemBinding.root.radioButton.isChecked = false
        }

        itemBinding.root.setPrimaryText(this.countryName)
        itemBinding.root.setLeadingEmojiIcon(countryEmoji)

        if (cities.size > 1) {
            itemBinding.root.setSecondaryText(
                String.format(
                    this@NetpGeoswitchingActivity.getString(R.string.netpGeoswitchingHeaderCountrySubtitle),
                    cities.size,
                ),
            )
            itemBinding.root.trailingIconContainer.show()
            itemBinding.root.setTrailingIconClickListener {
                // Automatically select the country before the user can choose the specific city
                itemBinding.root.radioButton.isChecked = true
                onItemMenuClicked(countryName, cities)
            }
        } else {
            itemBinding.root.secondaryText.gone()
            itemBinding.root.trailingIconContainer.gone()
        }

        itemBinding.root.radioButton.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked && view != lastSelectedButton) {
                lastSelectedButton?.isChecked = false
                lastSelectedButton = view
                viewModel.onCountrySelected(this.countryCode)
            }
        }

        // Automatically selects the country when the item is clicked
        itemBinding.root.setClickListener {
            itemBinding.root.radioButton.isChecked = true
        }
    }

    companion object {
        private const val TAG_DIALOG_CITY_CHOICE = "DIALOG_CITY_CHOICE"
    }
}

internal object NetpGeoswitchingScreenNoParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = NetpGeoswitchingScreenNoParams
}
