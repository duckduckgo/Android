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

package com.duckduckgo.app.systemsearch

import android.content.pm.PackageManager.NameNotFoundException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.systemsearch.DeviceAppSuggestionsAdapter.DeviceAppViewHolder
import kotlinx.android.synthetic.main.item_device_app_suggestion.view.*

class DeviceAppSuggestionsAdapter(
    private val clickListener: (DeviceApp) -> Unit
) : RecyclerView.Adapter<DeviceAppViewHolder>() {
    private var deviceApps: List<DeviceApp> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceAppViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_device_app_suggestion, parent, false)
        return DeviceAppViewHolder(root, root.icon, root.title)
    }

    override fun onBindViewHolder(holder: DeviceAppViewHolder, position: Int) {
        val app = deviceApps[position]
        holder.title.text = app.shortName
        holder.root.setOnClickListener {
            clickListener(app)
        }
        try {
            val drawable = app.retrieveIcon(holder.icon.context.packageManager)
            holder.icon.setImageDrawable(drawable)
        } catch (e: NameNotFoundException) {
            holder.icon.setImageDrawable(null)
        }
    }

    override fun getItemCount(): Int {
        return deviceApps.size
    }

    @UiThread
    fun updateData(newDeviceApps: List<DeviceApp>) {
        if (deviceApps == newDeviceApps) return
        deviceApps = newDeviceApps
        notifyDataSetChanged()
    }

    class DeviceAppViewHolder(
        val root: View,
        var icon: ImageView,
        val title: TextView
    ) : RecyclerView.ViewHolder(root)

}
