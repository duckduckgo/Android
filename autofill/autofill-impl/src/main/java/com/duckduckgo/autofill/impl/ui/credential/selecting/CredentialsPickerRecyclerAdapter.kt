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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerBinding
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ButtonType.*
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.CredentialsViewHolder
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show

class CredentialsPickerRecyclerAdapter(
    val lifecycleOwner: LifecycleOwner,
    val faviconManager: FaviconManager,
    val credentialTextExtractor: CredentialTextExtractor,
    private val credentials: List<LoginCredentials>,
    private val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
) : Adapter<CredentialsViewHolder>() {

    var showExpandedView = false

    private val buttonTypeDecider = ButtonTypeDecider()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialsViewHolder {
        val binding = ItemRowAutofillCredentialsPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CredentialsViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: CredentialsViewHolder, position: Int) {
        viewHolder.binding.configureCallToActionButton(position, credentials[position], credentials.size)
    }

    private fun ItemRowAutofillCredentialsPickerBinding.configureCallToActionButton(
        position: Int,
        credentials: LoginCredentials,
        fullCredentialListSize: Int,
    ) {
        val buttonType = buttonTypeDecider.determineButtonType(position, fullCredentialListSize, showExpandedView)

        val button = when (buttonType) {
            is UseCredentialPrimaryButton -> useCredentialPrimaryButton.also {
                it.text = credentialTextExtractor.usernameOrPlaceholder(credentials)
                it.setOnClickListener { onCredentialSelected(credentials) }
            }
            is UseCredentialSecondaryButton -> useCredentialSecondaryButton.also {
                it.text = credentialTextExtractor.usernameOrPlaceholder(credentials)
                it.setOnClickListener { onCredentialSelected(credentials) }
            }
            is ShowMoreButton -> moreOptionsButton.also {
                it.setOnClickListener {
                    showExpandedView = true
                    notifyDataSetChanged()
                }
            }
        }

        with(button) {
            this.show()
            hideOtherCallToActions(this)
        }
    }

    private fun ItemRowAutofillCredentialsPickerBinding.hideOtherCallToActions(buttonToKeep: Button?) {
        val hideableButtons = mutableListOf(
            useCredentialPrimaryButton,
            useCredentialSecondaryButton,
            moreOptionsButton,
        )

        hideableButtons
            .filterNot { it.id == buttonToKeep?.id }
            .forEach { it.gone() }
    }

    override fun getItemCount(): Int {
        return if (showExpandedView) {
            credentials.size
        } else {
            minOf(3, credentials.size)
        }
    }

    class CredentialsViewHolder(val binding: ItemRowAutofillCredentialsPickerBinding) : ViewHolder(binding.root)

    class ButtonTypeDecider {
        fun determineButtonType(listPosition: Int, fullListSize: Int, expandedMode: Boolean): ButtonType {
            return if (listPosition == 0) {
                UseCredentialPrimaryButton
            } else if (listPosition == 2 && fullListSize > 3 && !expandedMode) {
                ShowMoreButton
            } else {
                UseCredentialSecondaryButton
            }
        }
    }

    sealed interface ButtonType {
        object UseCredentialPrimaryButton : ButtonType
        object UseCredentialSecondaryButton : ButtonType
        object ShowMoreButton : ButtonType
    }
}
