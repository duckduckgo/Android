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

import android.os.*
import android.view.*
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.app.global.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.mobile.android.ui.viewbinding.*
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.*

@InjectWith(FragmentScope::class)
class SaveRecoveryCodeFragment : DuckDuckGoFragment(R.layout.fragment_recovery_code) {

    private val binding: FragmentRecoveryCodeBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.footerPrimaryButton.setOnClickListener {
        }
        binding.footerSecondaryButton.setOnClickListener {
        }
        binding.footerNextButton.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun observeUiEvents() {
    }

    companion object {
        fun instance() = SaveRecoveryCodeFragment()
    }
}
