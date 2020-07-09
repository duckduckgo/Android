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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.image.GlideApp
import com.duckduckgo.app.global.view.quietlySetIsChecked
import com.duckduckgo.app.location.data.LocationPermissionEntity
import kotlinx.android.synthetic.main.view_fireproof_website_entry.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_toggle.view.*
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionDelete
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionEntryDomain
import kotlinx.android.synthetic.main.view_location_permissions_entry.view.locationPermissionEntryFavicon
import kotlinx.android.synthetic.main.view_location_permissions_toggle.view.locationPermissionsToggle
import timber.log.Timber

class LocationPermissionsAdapter(
    private val viewModel: LocationPermissionsViewModel
) : RecyclerView.Adapter<LocationPermissionsViewHolder>() {

    companion object {
        const val PRECISE_LOCATION_DOMAIN_TYPE = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val TOGGLE_TYPE = 3
        const val DIVIDER_TYPE = 4

        const val EMPTY_HINT_ITEM_SIZE = 1
    }

    private val sortedHeaderElements = listOf(DESCRIPTION_TYPE, TOGGLE_TYPE, DIVIDER_TYPE)

    var locationPermissions: List<LocationPermissionEntity> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var locationPermissionEnabled: Boolean = false

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
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < sortedHeaderElements.size) {
            sortedHeaderElements[position]
        } else {
            getListItemType()
        }
    }

    override fun onBindViewHolder(holder: LocationPermissionsViewHolder, position: Int) {
        when (holder) {
            is LocationPermissionsViewHolder.LocationPermissionsToggleViewHolder -> {
                holder.bind(locationPermissionEnabled)
            }
            is LocationPermissionsViewHolder.LocationPermissionsItemViewHolder -> holder.bind(locationPermissions[getLocationPermissionPosition(position)])
        }
    }

    override fun getItemCount(): Int {
        return getItemsSize() + itemsOnTopOfList()
    }

    private fun getItemsSize() = if (locationPermissions.isEmpty()) {
        EMPTY_HINT_ITEM_SIZE
    } else {
        locationPermissions.size
    }

    private fun itemsOnTopOfList() = sortedHeaderElements.size

    private fun getLocationPermissionPosition(position: Int) = position - itemsOnTopOfList()

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

    class LocationPermissionsSimpleViewViewHolder(itemView: View) : LocationPermissionsViewHolder(itemView)

    class LocationPermissionsItemViewHolder(itemView: View, private val viewModel: LocationPermissionsViewModel) : LocationPermissionsViewHolder(itemView) {

        lateinit var entity: LocationPermissionEntity

        fun bind(entity: LocationPermissionEntity) {
            this.entity = entity

            itemView.locationPermissionDelete.contentDescription = itemView.context.getString(
                R.string.preciseLocationDeleteContentDescription,
                entity.domain
            )

            itemView.locationPermissionEntryDomain.text = entity.domain
            loadFavicon(entity.domain)

            itemView.locationPermissionDelete.setOnClickListener {
                deleteEntity(entity)
            }
        }

        private fun loadFavicon(domain: String) {
            val faviconUrl = Uri.parse(domain).faviconLocation()

            GlideApp.with(itemView)
                .load(faviconUrl)
                .placeholder(R.drawable.ic_globe_gray_16dp)
                .error(R.drawable.ic_globe_gray_16dp)
                .into(itemView.locationPermissionEntryFavicon)
        }

        private fun deleteEntity(entity: LocationPermissionEntity) {
            Timber.i("Deleting permissions from domain: ${entity.domain}")
            viewModel.onDeleteRequested(entity)
        }
    }
}
