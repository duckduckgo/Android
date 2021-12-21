/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.location.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.isGone
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsDescriptionBinding
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsDividerBinding
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsEmptyHintBinding
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsEntryBinding
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsSectionTitleBinding
import com.duckduckgo.app.browser.databinding.ViewLocationPermissionsToggleBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import kotlinx.coroutines.launch
import timber.log.Timber

class LocationPermissionsAdapter(
    private val viewModel: LocationPermissionsViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : RecyclerView.Adapter<LocationPermissionsViewHolder>() {

    private var allowedLocationPermissions: MutableList<LocationPermissionEntity> = mutableListOf()
    private var deniedLocationPermissions: MutableList<LocationPermissionEntity> = mutableListOf()

    companion object {
        const val PRECISE_LOCATION_DOMAIN_TYPE = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val TOGGLE_TYPE = 3
        const val DIVIDER_TYPE = 4
        const val ALLOWED_SITES_SECTION_TITLE_TYPE = 5
        const val DENIED_SITES_SECTION_TITLE_TYPE = 6

        const val EMPTY_HINT_ITEM_SIZE = 1
    }

    fun updatePermissions(
        isLocationPermissionEnabled: Boolean,
        newPermissions: List<LocationPermissionEntity>
    ) {
        locationPermissionEnabled = isLocationPermissionEnabled
        locationPermissions = newPermissions
        notifyDataSetChanged()
    }

    private var locationPermissionEnabled: Boolean = false

    private var locationPermissions: List<LocationPermissionEntity> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                allowedLocationPermissions.clear()
                deniedLocationPermissions.clear()
                value.forEach {
                    if (it.permission == LocationPermissionType.ALLOW_ONCE ||
                        it.permission == LocationPermissionType.ALLOW_ALWAYS) {
                        allowedLocationPermissions.add(it)
                    } else {
                        deniedLocationPermissions.add(it)
                    }
                }
            }
        }

    private fun getSortedHeaderElements(): List<Int> {
        return if (allowedLocationPermissions.isEmpty()) {
            listOf(DESCRIPTION_TYPE, TOGGLE_TYPE, DIVIDER_TYPE)
        } else {
            listOf(DESCRIPTION_TYPE, TOGGLE_TYPE, DIVIDER_TYPE, ALLOWED_SITES_SECTION_TITLE_TYPE)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): LocationPermissionsViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DESCRIPTION_TYPE -> {
                val binding =
                    ViewLocationPermissionsDescriptionBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(binding)
            }
            TOGGLE_TYPE -> {
                val binding = ViewLocationPermissionsToggleBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsToggleViewHolder(
                    binding,
                    CompoundButton.OnCheckedChangeListener { _, isChecked ->
                        viewModel.onLocationPermissionToggled(isChecked)
                    })
            }
            DIVIDER_TYPE -> {
                val binding = ViewLocationPermissionsDividerBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(binding)
            }
            PRECISE_LOCATION_DOMAIN_TYPE -> {
                val binding = ViewLocationPermissionsEntryBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsItemViewHolder(
                    inflater, binding, viewModel, lifecycleOwner, faviconManager)
            }
            EMPTY_STATE_TYPE -> {
                val binding =
                    ViewLocationPermissionsEmptyHintBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(binding)
            }
            ALLOWED_SITES_SECTION_TITLE_TYPE -> {
                val binding =
                    ViewLocationPermissionsSectionTitleBinding.inflate(inflater, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsAllowedSectionViewHolder(binding)
            }
            DENIED_SITES_SECTION_TITLE_TYPE -> {
                val binding =
                    ViewLocationPermissionsSectionTitleBinding.inflate(inflater, parent, false)
                binding.locationPermissionsSectionTitle.setText(
                    R.string.preciseLocationDeniedSitesSectionTitle)
                LocationPermissionsViewHolder.LocationPermissionsDeniedSectionViewHolder(binding)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < getSortedHeaderElements().size) {
            getSortedHeaderElements()[position]
        } else if (deniedLocationPermissions.isNotEmpty() &&
            position == getSortedHeaderElements().size + allowedLocationPermissions.size) {
            DENIED_SITES_SECTION_TITLE_TYPE
        } else {
            getListItemType()
        }
    }

    override fun onBindViewHolder(holder: LocationPermissionsViewHolder, position: Int) {
        when (holder) {
            is LocationPermissionsViewHolder.LocationPermissionsToggleViewHolder -> {
                holder.bind(locationPermissionEnabled)
            }
            is LocationPermissionsViewHolder.LocationPermissionsAllowedSectionViewHolder -> {
                holder.bind(allowedLocationPermissions.isNotEmpty())
            }
            is LocationPermissionsViewHolder.LocationPermissionsDeniedSectionViewHolder -> {
                holder.bind(deniedLocationPermissions.isNotEmpty())
            }
            is LocationPermissionsViewHolder.LocationPermissionsItemViewHolder -> {
                holder.bind(getLocationPermission(position))
            }
        }
    }

    override fun getItemCount(): Int {
        return getItemsSize() + itemsNotOnList()
    }

    private fun getItemsSize() =
        if (locationPermissions.isEmpty()) {
            EMPTY_HINT_ITEM_SIZE
        } else {
            locationPermissions.size
        }

    private fun itemsNotOnList(): Int {
        return if (deniedLocationPermissions.isEmpty()) {
            getSortedHeaderElements().size
        } else {
            getSortedHeaderElements().size + 1
        }
    }

    private fun getLocationPermission(position: Int): LocationPermissionEntity {
        return if (allowedLocationPermissions.isNotEmpty()) {
            if (position >= getSortedHeaderElements().size &&
                position < getSortedHeaderElements().size + allowedLocationPermissions.size) {
                allowedLocationPermissions[position - getSortedHeaderElements().size]
            } else {
                deniedLocationPermissions[
                    position - itemsNotOnList() - allowedLocationPermissions.size]
            }
        } else {
            deniedLocationPermissions[position - itemsNotOnList()]
        }
    }

    private fun getListItemType(): Int {
        return if (locationPermissions.isEmpty()) {
            EMPTY_STATE_TYPE
        } else {
            PRECISE_LOCATION_DOMAIN_TYPE
        }
    }
}

sealed class LocationPermissionsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class LocationPermissionsToggleViewHolder(
        private val binding: ViewLocationPermissionsToggleBinding,
        private val listener: CompoundButton.OnCheckedChangeListener
    ) : LocationPermissionsViewHolder(binding.root) {
        fun bind(locationPermissionEnabled: Boolean) {
            binding.locationPermissionsToggle.quietlySetIsChecked(
                locationPermissionEnabled, listener)
        }
    }

    class LocationPermissionsAllowedSectionViewHolder(
        private val binding: ViewLocationPermissionsSectionTitleBinding
    ) : LocationPermissionsViewHolder(binding.root) {
        fun bind(allowedPermissions: Boolean) {
            binding.locationPermissionsSectionTitle.setText(
                R.string.preciseLocationAllowedSitesSectionTitle)
            binding.root.isGone = !allowedPermissions
        }
    }

    class LocationPermissionsDeniedSectionViewHolder(
        private val binding: ViewLocationPermissionsSectionTitleBinding
    ) : LocationPermissionsViewHolder(binding.root) {
        fun bind(deniedPermissionsItems: Boolean) {
            binding.locationPermissionsSectionTitle.setText(
                R.string.preciseLocationDeniedSitesSectionTitle)
            binding.locationPermissionsSectionTitle.isGone = !deniedPermissionsItems
        }
    }

    class LocationPermissionsSimpleViewViewHolder(binding: ViewBinding) :
        LocationPermissionsViewHolder(binding.root)

    class LocationPermissionsItemViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: ViewLocationPermissionsEntryBinding,
        private val viewModel: LocationPermissionsViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : LocationPermissionsViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private lateinit var entity: LocationPermissionEntity

        fun bind(entity: LocationPermissionEntity) {
            val singleListItem = binding.root

            this.entity = entity
            val website = entity.domain.websiteFromGeoLocationsApiOrigin()

            singleListItem.contentDescription =
                context.getString(R.string.preciseLocationDeleteContentDescription, website)

            singleListItem.setTitle(website)
            loadFavicon(entity.domain)

            singleListItem.setOverflowClickListener { anchor -> showOverFlowMenu(anchor, entity) }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(
                    url = url, view = itemView.findViewById(R.id.image))
            }
        }

        private fun showOverFlowMenu(anchor: View, entity: LocationPermissionEntity) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { editEntity(entity) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { deleteEntity(entity) }
            }
            popupMenu.show(binding.root, anchor)
        }

        private fun editEntity(entity: LocationPermissionEntity) {
            Timber.i("Edit permissions from domain: ${entity.domain}")
            viewModel.onEditRequested(entity)
        }

        private fun deleteEntity(entity: LocationPermissionEntity) {
            Timber.i("Deleting permissions from domain: ${entity.domain}")
            viewModel.onDeleteRequested(entity)
        }
    }
}
