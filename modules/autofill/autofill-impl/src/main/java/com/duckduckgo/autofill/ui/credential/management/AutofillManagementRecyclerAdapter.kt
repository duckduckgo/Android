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

package com.duckduckgo.autofill.ui.credential.management

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenBinding
import kotlinx.coroutines.launch
import timber.log.Timber

class AutofillManagementRecyclerAdapter(
    val lifecycleOwner: LifecycleOwner,
    val faviconManager: FaviconManager,
    val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
    val onCopyUsername: (credentials: LoginCredentials) -> Unit,
    val onCopyPassword: (credentials: LoginCredentials) -> Unit
) : Adapter<AutofillManagementRecyclerAdapter.CredentialsViewHolder>() {

    private var credentials = listOf<LoginCredentials>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CredentialsViewHolder {
        val binding = ItemRowAutofillCredentialsManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CredentialsViewHolder(binding)
    }

    override fun onBindViewHolder(
        viewHolder: CredentialsViewHolder,
        position: Int
    ) {
        val credentials = credentials[position]

        with(viewHolder.binding) {
            username.text = credentials.username
            domain.text = credentials.domain

            root.setOnClickListener { onCredentialSelected(credentials) }
            // root.setOnClickListener { onCopyUsername(credentials) }
            root.setOnLongClickListener { onCopyPassword(credentials); true }

            updateFavicon(credentials)
        }

    }

    private fun ItemRowAutofillCredentialsManagementScreenBinding.updateFavicon(credentials: LoginCredentials) {
        val domain = credentials.domain
        if (domain == null) {
            favicon.setImageBitmap(null)
        } else {
            lifecycleOwner.lifecycleScope.launch {
                Timber.e("url for favicon is %s", domain)
                faviconManager.loadToViewFromLocalOrFallback(url = domain, view = favicon)
            }
        }
    }

    fun updateLogins(list: List<LoginCredentials>) {
        credentials = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = credentials.size

    class CredentialsViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
}
