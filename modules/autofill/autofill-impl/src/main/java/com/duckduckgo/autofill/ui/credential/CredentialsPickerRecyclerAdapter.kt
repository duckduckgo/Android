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

package com.duckduckgo.autofill.ui.credential

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerBinding
import com.duckduckgo.autofill.ui.credential.CredentialsPickerRecyclerAdapter.CredentialsViewHolder
import timber.log.Timber

class CredentialsPickerRecyclerAdapter(
    val lifecycleOwner: LifecycleOwner,
    val faviconManager: FaviconManager,
    private val credentials: List<LoginCredentials>,
    private val onCredentialSelected: (credentials: LoginCredentials) -> Unit
) : Adapter<CredentialsViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CredentialsViewHolder {
        val binding = ItemRowAutofillCredentialsPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CredentialsViewHolder(binding)
    }

    override fun onBindViewHolder(
        viewHolder: CredentialsViewHolder,
        position: Int
    ) {
        val credentials = credentials[position]
        with(viewHolder.binding) {
            useCredentialButton.text = credentials.username

            useCredentialButton.setOnClickListener {
                Timber.i("selected %s", credentials.username)
                onCredentialSelected(credentials)
            }
        }

    }

    override fun getItemCount(): Int = credentials.size

    class CredentialsViewHolder(val binding: ItemRowAutofillCredentialsPickerBinding) : ViewHolder(binding.root)
}
