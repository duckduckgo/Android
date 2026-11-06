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

package com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.site.permissions.impl.databinding.ItemRemoveSitePermissionsBinding

class RemovePermissionsAdapter(
    private val onRemoveClicked: () -> Unit,
) : RecyclerView.Adapter<RemovePermissionsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemRemoveSitePermissionsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.removePermissionsButton.setOnClickListener { onRemoveClicked() }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = Unit

    override fun getItemCount(): Int = 1

    class ViewHolder(binding: ItemRemoveSitePermissionsBinding) : RecyclerView.ViewHolder(binding.root)
}
