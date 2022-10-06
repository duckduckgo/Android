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

package com.duckduckgo.app.sitepermissions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.layout
import com.duckduckgo.app.browser.databinding.ViewSitePermissionsDescriptionBinding
import com.duckduckgo.app.browser.databinding.ViewSitePermissionsEmptyListBinding
import com.duckduckgo.app.browser.databinding.ViewSitePermissionsTitleBinding
import com.duckduckgo.app.browser.databinding.ViewSitePermissionsToggleBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.Divider
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.EmptySites
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.SiteAllowedItem
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.SitePermissionToggle
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.SitePermissionsDescription
import com.duckduckgo.app.sitepermissions.SitePermissionListItem.SitePermissionsHeader
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.DESCRIPTION
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.DIVIDER
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.HEADER
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.SITES_EMPTY
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.SITE_ALLOWED_ITEM
import com.duckduckgo.app.sitepermissions.SitePermissionsListViewType.TOGGLE
import com.duckduckgo.mobile.android.databinding.RowOneLineListItemBinding
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.ui.view.divider.HorizontalDivider
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import kotlinx.coroutines.launch

class SitePermissionsAdapter(
    private val viewModel: SitePermissionsViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SitePermissionListItem> = listOf()
    private var sitesEmpty: Boolean = true

    fun updateItems(sites: List<String>, isLocationEnabled: Boolean, isCameraEnabled: Boolean, isMicEnabled: Boolean) {
        val listItems = mutableListOf<SitePermissionListItem>()
        listItems.add(SitePermissionsDescription())
        listItems.add(SitePermissionsHeader(R.string.sitePermissionsSettingsEnablePermissionTitle))
        listItems.add(SitePermissionToggle(R.string.sitePermissionsSettingsLocation, isLocationEnabled))
        listItems.add(SitePermissionToggle(R.string.sitePermissionsSettingsCamera, isCameraEnabled))
        listItems.add(SitePermissionToggle(R.string.sitePermissionsSettingsMicrophone, isMicEnabled))
        listItems.add(Divider())
        listItems.add(SitePermissionsHeader(R.string.sitePermissionsSettingsAllowedSitesTitle))
        if (sites.isEmpty()) {
            listItems.add(EmptySites())
        } else {
            sites.forEach { listItems.add(SiteAllowedItem(it)) }
        }
        items = listItems
        sitesEmpty = sites.isEmpty()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (SitePermissionsListViewType.values()[viewType]) {
            DESCRIPTION -> {
                val binding = ViewSitePermissionsDescriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SitePermissionsSimpleViewHolder(binding)
            }
            HEADER -> {
                val binding = ViewSitePermissionsTitleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SitePermissionsHeaderViewHolder(binding, LayoutInflater.from(parent.context), viewModel)
            }
            TOGGLE -> {
                val binding = ViewSitePermissionsToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SitePermissionToggleViewHolder(binding)
            }
            DIVIDER -> {
                val view = HorizontalDivider(parent.context)
                SitePermissionsDividerViewHolder(view)
            }
            SITES_EMPTY -> {
                val binding = ViewSitePermissionsEmptyListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SitePermissionsSimpleViewHolder(binding)
            }
            SITE_ALLOWED_ITEM -> {
                val binding = RowOneLineListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SiteViewHolder(binding, viewModel, lifecycleOwner, faviconManager)
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SitePermissionsHeader -> (holder as SitePermissionsHeaderViewHolder).bind(item.title, sitesEmpty)
            is SitePermissionToggle -> (holder as SitePermissionToggleViewHolder).bind(item) { _, isChecked ->
                viewModel.permissionToggleSelected(isChecked, item.text)
            }
            is SiteAllowedItem -> (holder as SiteViewHolder).bind(item)
            else -> {}
        }
    }

    override fun getItemViewType(position: Int) = items[position].viewType.ordinal

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolders
     */
    class SitePermissionsSimpleViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

    class SitePermissionsDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class SitePermissionsHeaderViewHolder(
        private val binding: ViewSitePermissionsTitleBinding,
        private val layoutInflater: LayoutInflater,
        private val viewModel: SitePermissionsViewModel
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: Int, isListEmpty: Boolean) {
            when (title) {
                R.string.sitePermissionsSettingsAllowedSitesTitle -> {
                    binding.overflowMenu.apply {
                        show()
                        setOnClickListener { showOverflowMenu(isListEmpty) }
                    }
                }
                else -> binding.overflowMenu.gone()
            }
            binding.sitePermissionsSectionTitle.setText(title)
        }

        private fun showOverflowMenu(removeDisabled: Boolean) {
            val popupMenu = PopupMenu(layoutInflater, layout.popup_window_remove_all_menu)
            val menuItem = popupMenu.contentView.findViewById<TextView>(R.id.removeAll)
            if (removeDisabled) menuItem.isEnabled = false
            popupMenu.apply {
                onMenuItemClicked(menuItem) { viewModel.removeAllSitesSelected() }
                show(binding.root, binding.overflowMenu)
            }
        }
    }

    class SitePermissionToggleViewHolder(val binding: ViewSitePermissionsToggleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: SitePermissionToggle,
            listener: CompoundButton.OnCheckedChangeListener
        ) {
            binding.sitePermissionsToggle.setText(item.text)
            binding.sitePermissionsToggle.quietlySetIsChecked(item.enable, listener)
            val iconRes = when (item.text) {
                R.string.sitePermissionsSettingsLocation -> {
                    if (item.enable) R.drawable.ic_location
                    else R.drawable.ic_location_disabled
                }
                R.string.sitePermissionsSettingsCamera -> {
                    if (item.enable) R.drawable.ic_camera
                    else R.drawable.ic_camera_disabled
                }
                R.string.sitePermissionsSettingsMicrophone -> {
                    if (item.enable) R.drawable.ic_microphone
                    else R.drawable.ic_microphone_disabled
                }
                else -> null
            }
            iconRes?.let {
                binding.sitePermissionToggleIcon.setImageResource(it)
            }
        }
    }

    class SiteViewHolder(
        private val binding: RowOneLineListItemBinding,
        private val viewModel: SitePermissionsViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SiteAllowedItem) {
            val oneListItem = binding.root
            oneListItem.setPrimaryText(item.domain)
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(url = item.domain, view = oneListItem.leadingIcon())
            }
            oneListItem.setClickListener {
                viewModel.allowedSiteSelected(item.domain)
            }
        }
    }
}

/**
 * Data classes
 */
sealed class SitePermissionListItem(val viewType: SitePermissionsListViewType) {
    class SitePermissionsDescription : SitePermissionListItem(DESCRIPTION)
    data class SitePermissionsHeader(@StringRes val title: Int) : SitePermissionListItem(HEADER)
    data class SitePermissionToggle(@StringRes val text: Int, val enable: Boolean) : SitePermissionListItem(TOGGLE)
    class Divider : SitePermissionListItem(DIVIDER)
    data class SiteAllowedItem(val domain: String) : SitePermissionListItem(SITE_ALLOWED_ITEM)
    class EmptySites : SitePermissionListItem(SITES_EMPTY)
}

enum class SitePermissionsListViewType {
    DESCRIPTION,
    HEADER,
    TOGGLE,
    DIVIDER,
    SITE_ALLOWED_ITEM,
    SITES_EMPTY
}
