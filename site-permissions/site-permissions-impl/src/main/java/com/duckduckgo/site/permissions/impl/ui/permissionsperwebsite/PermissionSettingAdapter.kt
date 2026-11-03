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

package com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.site.permissions.impl.databinding.ItemSitePermissionSettingSelectionBinding
import com.duckduckgo.site.permissions.impl.ui.GlobalPermission
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionSettingAdapter.ViewHolder
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK_DISABLED
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.DENY

class PermissionSettingAdapter(
    private val viewModel: PermissionsPerWebsiteViewModel,
    private val permissionSettingsRedesign: Boolean,
) : RecyclerView.Adapter<ViewHolder>() {

    private var items: List<WebsitePermissionSetting> = listOf()

    fun updateItems(settings: List<WebsitePermissionSetting>) {
        items = settings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            ItemSitePermissionSettingSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            viewModel,
            permissionSettingsRedesign,
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ItemSitePermissionSettingSelectionBinding,
        private val viewModel: PermissionsPerWebsiteViewModel,
        private val permissionSettingsRedesign: Boolean,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(setting: WebsitePermissionSetting) {
            val context = binding.root.context
            binding.permissionSettingListItem.apply {
                if (permissionSettingsRedesign) {
                    val permission = GlobalPermission.from(setting.title)
                    val blocked = setting.setting == ASK_DISABLED || setting.setting == DENY
                    setLeadingIconResource(permission?.let { if (blocked) it.blockedIcon else it.icon } ?: setting.icon)
                    setPrimaryText(context.getString(permission?.settingTitle ?: setting.title))
                    setSecondaryText(context.getString(setting.setting.redesignedStringRes))
                } else {
                    setLeadingIconResource(setting.icon)
                    setPrimaryText(context.getString(setting.title))
                    setSecondaryText(context.getString(setting.setting.stringRes))
                }
                setOnClickListener { viewModel.permissionSettingSelected(setting) }
            }
        }
    }
}
