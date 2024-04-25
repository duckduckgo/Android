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

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ItemGeoswitchingCountryBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemGeoswitchingDividerBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemGeoswitchingHeaderBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemGeoswitchingRecommendedBinding
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.CountryItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.DividerItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.HeaderItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.RecommendedItem

class NetpGeoswitchingAdapter constructor(
    initialSelectedCountryCode: String?,
    private val onItemMenuClicked: (String, List<String>) -> Unit,
    private val onCountrySelected: (String) -> Unit,
    private val onNearestAvailableSelected: () -> Unit,
) : ListAdapter<GeoswitchingListItem, ViewHolder>(GeoswitchingDiffCallback()) {

    private var currentSelectedCountryCode = initialSelectedCountryCode
    private var lastCheckedRadioButton: CompoundButton? = null
    private val updateLastCheckedItemListener = OnCheckedChangeListener { button, isChecked ->
        // We only want to update the state if the new button selected is not the previous one
        if (isChecked && lastCheckedRadioButton != button) {
            lastCheckedRadioButton?.isChecked = false // Uncheck the previously selected
            lastCheckedRadioButton = button
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HeaderItem -> HEADER_VIEW_TYPE
            is RecommendedItem -> RECOMMENDED_VIEW_TYPE
            is DividerItem -> DIVIDER_VIEW_TYPE
            is CountryItem -> COUNTRY_VIEW_TYPE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            HEADER_VIEW_TYPE -> HeaderViewHolder(
                ItemGeoswitchingHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            RECOMMENDED_VIEW_TYPE -> RecommendedViewHolder(
                ItemGeoswitchingRecommendedBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                updateLastCheckedItemListener,
                onNearestAvailableSelected,
            )
            DIVIDER_VIEW_TYPE -> DividerViewHolder(
                ItemGeoswitchingDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            COUNTRY_VIEW_TYPE -> CountryViewHolder(
                ItemGeoswitchingCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                updateLastCheckedItemListener,
                onItemMenuClicked,
                onCountrySelected,
            )
            else -> CountryViewHolder(
                ItemGeoswitchingCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                updateLastCheckedItemListener,
                onItemMenuClicked,
                onCountrySelected,
            )
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as HeaderItem)
            is RecommendedViewHolder -> holder.bind(getItem(position) as RecommendedItem, currentSelectedCountryCode)
            is DividerViewHolder -> {}
            is CountryViewHolder -> holder.bind(getItem(position) as CountryItem, currentSelectedCountryCode)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemGeoswitchingHeaderBinding,
    ) : ViewHolder(binding.root) {
        fun bind(headerItem: HeaderItem) {
            binding.root.primaryText = binding.root.context.getString(headerItem.header)
        }
    }

    private class RecommendedViewHolder(
        private val binding: ItemGeoswitchingRecommendedBinding,
        private val updateLastCheckedItemListener: OnCheckedChangeListener,
        private val onNearestAvailableSelected: () -> Unit,
    ) : ViewHolder(binding.root) {
        fun bind(
            recommendedItem: RecommendedItem,
            currentSelectedCountryCode: String?,
        ) {
            with(binding.root) {
                setPrimaryText(context.getString(recommendedItem.title))
                setSecondaryText(context.getString(recommendedItem.subtitle))

                // Sets initial state
                if (currentSelectedCountryCode.isNullOrEmpty()) {
                    radioButton.isChecked = true
                    updateLastCheckedItemListener.onCheckedChanged(radioButton, true)
                }

                radioButton.setOnCheckedChangeListener { buttonView, isChecked ->
                    updateLastCheckedItemListener.onCheckedChanged(buttonView, isChecked)
                    if (isChecked) onNearestAvailableSelected()
                }

                // Automatically selects nearest available when the item is clicked
                setClickListener {
                    radioButton.isChecked = true
                }
            }
        }
    }

    private class DividerViewHolder(binding: ItemGeoswitchingDividerBinding) : ViewHolder(binding.root)

    private class CountryViewHolder(
        private val binding: ItemGeoswitchingCountryBinding,
        private val updateLastCheckedItemListener: OnCheckedChangeListener,
        private val onItemMenuClicked: (String, List<String>) -> Unit,
        private val onCountrySelected: (String) -> Unit,
    ) : ViewHolder(binding.root) {
        fun bind(
            countryItem: CountryItem,
            currentSelectedCountryCode: String?,
        ) {
            with(binding.root) {
                // Sets initial state
                if (currentSelectedCountryCode == countryItem.countryCode) {
                    radioButton.isChecked = true
                    updateLastCheckedItemListener.onCheckedChanged(radioButton, true)
                }

                setPrimaryText(countryItem.countryName)
                setLeadingEmojiIcon(countryItem.countryEmoji)

                if (countryItem.cities.size > 1) {
                    setSecondaryText(String.format(context.getString(R.string.netpGeoswitchingHeaderCountrySubtitle), countryItem.cities.size))
                    trailingIconContainer.show()
                    setTrailingIconClickListener {
                        // Automatically select the country before the user can choose the specific city
                        radioButton.isChecked = true
                        onItemMenuClicked(countryItem.countryName, countryItem.cities)
                    }
                } else {
                    secondaryText.gone()
                    trailingIconContainer.gone()
                }

                radioButton.setOnCheckedChangeListener { buttonView, isChecked ->
                    updateLastCheckedItemListener.onCheckedChanged(buttonView, isChecked)
                    if (isChecked) onCountrySelected(countryItem.countryCode)
                }

                // Automatically selects the country when the item is clicked
                setClickListener {
                    radioButton.isChecked = true
                }
            }
        }
    }

    companion object {
        private const val HEADER_VIEW_TYPE = 0
        private const val RECOMMENDED_VIEW_TYPE = 1
        private const val DIVIDER_VIEW_TYPE = 2
        private const val COUNTRY_VIEW_TYPE = 3
    }
}
sealed class GeoswitchingListItem {
    data class HeaderItem(@StringRes val header: Int) : GeoswitchingListItem()
    data class RecommendedItem(
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
    ) : GeoswitchingListItem()

    object DividerItem : GeoswitchingListItem()
    data class CountryItem(
        val countryCode: String,
        val countryEmoji: String,
        val countryName: String,
        val cities: List<String>,
    ) : GeoswitchingListItem()
}

private class GeoswitchingDiffCallback : DiffUtil.ItemCallback<GeoswitchingListItem>() {
    override fun areItemsTheSame(
        oldItem: GeoswitchingListItem,
        newItem: GeoswitchingListItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: GeoswitchingListItem,
        newItem: GeoswitchingListItem,
    ): Boolean {
        return oldItem == newItem
    }
}
