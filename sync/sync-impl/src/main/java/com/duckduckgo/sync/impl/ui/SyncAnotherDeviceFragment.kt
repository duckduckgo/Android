/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.os.Bundle
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentEnableSyncBinding
import com.duckduckgo.sync.impl.ui.EnableSyncFragment.EnableSyncListener

@InjectWith(FragmentScope::class)
class SyncAnotherDeviceFragment : DuckDuckGoFragment(R.layout.fragment_enable_sync) {

    interface SyncAnotherDeviceListener {
        fun syncAnotherDevice()
        fun createAccount()
    }

    private val binding: FragmentEnableSyncBinding by viewBinding()

    private val listener: SyncAnotherDeviceListener?
        get() = activity as? SyncAnotherDeviceListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.footerSecondaryButton.setOnClickListener {
            listener?.createAccount()
        }
        binding.closeIcon.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun observeUiEvents() {
        binding.contentIllustration.setImageResource(R.drawable.ic_connect_device_128)
        binding.contentTitle.text = "Sync Another Device?"
        binding.contentBody.text = "Your bookmarks will be backed up! Would you like to sync another device now?\n\nIf you’ve already set up Sync on another device, this will allow you to combine bookmarks from both devices into a single backup."
        binding.footerPrimaryButton.text = "Sync Another Device"
        binding.footerSecondaryButton.text = "Not Now"
    }

    companion object {
        fun instance() = SyncAnotherDeviceFragment()
    }
}

