/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.permissions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.databinding.ViewGeneralPermissionsBinding

class GeneralPermissionsAdapter(
    private val onNotificationsClicked: () -> Unit,
    private val onAppLinksClicked: () -> Unit,
) : RecyclerView.Adapter<GeneralPermissionsAdapter.ViewHolder>() {

    private var notificationsSubtitle = ""
    private var appLinksSubtitle = ""

    fun update(
        notificationsSubtitle: String,
        appLinksSubtitle: String,
    ) {
        this.notificationsSubtitle = notificationsSubtitle
        this.appLinksSubtitle = appLinksSubtitle
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ViewGeneralPermissionsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.notificationsSetting.setClickListener { onNotificationsClicked() }
        binding.appLinksSetting.setClickListener { onAppLinksClicked() }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.binding.notificationsSetting.setSecondaryText(notificationsSubtitle)
        holder.binding.appLinksSetting.setSecondaryText(appLinksSubtitle)
    }

    override fun getItemCount() = 1

    class ViewHolder(val binding: ViewGeneralPermissionsBinding) : RecyclerView.ViewHolder(binding.root)
}
