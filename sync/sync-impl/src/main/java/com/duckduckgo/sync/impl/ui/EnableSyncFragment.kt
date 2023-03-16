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

@InjectWith(FragmentScope::class)
class EnableSyncFragment : DuckDuckGoFragment(R.layout.fragment_enable_sync) {

    private val binding: FragmentEnableSyncBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.closeIcon.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun observeUiEvents() {
    }

    companion object {
        fun instance() = EnableSyncFragment()
    }
}
