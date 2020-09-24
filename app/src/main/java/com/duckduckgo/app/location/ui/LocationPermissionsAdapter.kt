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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.quietlySetIsChecked
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionEntryDomain
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionEntryFavicon
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionMenu
import kotlinx.android.synthetic.main.view_location_permissions_section_title.view.locationPermissionsSectionTitle
import kotlinx.android.synthetic.main.view_location_permissions_toggle.view.locationPermissionsToggle
import timber.log.Timber

class LocationPermissionsAdapter(
    private val viewModel: LocationPermissionsViewModel
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
                    if (it.permission == LocationPermissionType.ALLOW_ONCE || it.permission == LocationPermissionType.ALLOW_ALWAYS) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationPermissionsViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DESCRIPTION_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_description, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(view)
            }
            TOGGLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_toggle, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsToggleViewHolder(view,
                    CompoundButton.OnCheckedChangeListener { _, isChecked -> viewModel.onLocationPermissionToggled(isChecked) })
            }
            DIVIDER_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_divider, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(view)
            }
            PRECISE_LOCATION_DOMAIN_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_entry, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsItemViewHolder(view, viewModel)
            }
            EMPTY_STATE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_empty_hint, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsSimpleViewViewHolder(view)
            }
            ALLOWED_SITES_SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_section_title, parent, false)
                LocationPermissionsViewHolder.LocationPermissionsAllowedSectionViewHolder(view)
            }
            DENIED_SITES_SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_section_title, parent, false)
                view.locationPermissionsSectionTitle.setText(R.string.preciseLocationDeniedSitesSectionTitle)
                LocationPermissionsViewHolder.LocationPermissionsDeniedSectionViewHolder(view)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < getSortedHeaderElements().size) {
            getSortedHeaderElements()[position]
        } else if (deniedLocationPermissions.isNotEmpty() && position == getSortedHeaderElements().size + allowedLocationPermissions.size) {
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

    private fun getItemsSize() = if (locationPermissions.isEmpty()) {
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
            if (position >= getSortedHeaderElements().size && position < getSortedHeaderElements().size + allowedLocationPermissions.size) {
                allowedLocationPermissions[position - getSortedHeaderElements().size]
            } else {
                deniedLocationPermissions[position - itemsNotOnList() - allowedLocationPermissions.size]
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

    class LocationPermissionsToggleViewHolder(itemView: View, private val listener: CompoundButton.OnCheckedChangeListener) :
        LocationPermissionsViewHolder(itemView) {
        fun bind(locationPermissionEnabled: Boolean) {
            itemView.locationPermissionsToggle.quietlySetIsChecked(locationPermissionEnabled, listener)
        }
    }

    class LocationPermissionsAllowedSectionViewHolder(itemView: View) :
        LocationPermissionsViewHolder(itemView) {
        fun bind(allowedPermissions: Boolean) {
            itemView.locationPermissionsSectionTitle.setText(R.string.preciseLocationAllowedSitesSectionTitle)
            itemView.isGone = !allowedPermissions
        }
    }

    class LocationPermissionsDeniedSectionViewHolder(itemView: View) :
        LocationPermissionsViewHolder(itemView) {
        fun bind(deniedPermissionsItems: Boolean) {
            itemView.locationPermissionsSectionTitle.setText(R.string.preciseLocationDeniedSitesSectionTitle)
            itemView.locationPermissionsSectionTitle.isGone = !deniedPermissionsItems
        }
    }

    class LocationPermissionsSimpleViewViewHolder(itemView: View) : LocationPermissionsViewHolder(itemView)

    class LocationPermissionsItemViewHolder(itemView: View, private val viewModel: LocationPermissionsViewModel) :
        LocationPermissionsViewHolder(itemView) {

        lateinit var entity: LocationPermissionEntity

        fun bind(entity: LocationPermissionEntity) {
            this.entity = entity
            val website = entity.domain.websiteFromGeoLocationsApiOrigin()

            itemView.locationPermissionMenu.contentDescription = itemView.context.getString(
                R.string.preciseLocationDeleteContentDescription,
                website
            )

            itemView.locationPermissionEntryDomain.text = website
            loadFavicon(entity.domain)

            itemView.locationPermissionMenu.setOnClickListener {
                showOverFlowMenu(itemView.locationPermissionMenu, entity)
            }
        }

        private fun loadFavicon(domain: String) {
            viewModel.loadFavicon(domain, itemView.locationPermissionEntryFavicon)
        }

        private fun showOverFlowMenu(overflowMenu: ImageView, entity: LocationPermissionEntity) {
            val popup = PopupMenu(overflowMenu.context, overflowMenu)
            popup.inflate(R.menu.location_permissions_individual_overflow_menu)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.edit -> {
                        editEntity(entity); true
                    }
                    R.id.delete -> {
                        deleteEntity(entity); true
                    }
                    else -> false
                }
            }
            popup.show()
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
