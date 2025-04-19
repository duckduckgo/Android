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

package com.duckduckgo.subscriptions.impl.pir

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos.api.MacOsScreenWithEmptyParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivityPirBinding
import com.duckduckgo.subscriptions.impl.pir.PirActivity.Companion.PirScreenWithEmptyParams
import com.duckduckgo.windows.api.ui.WindowsScreenWithEmptyParams
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirScreenWithEmptyParams::class)
class PirActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ActivityPirBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupInternalToolbar()
        setupListeners()
        if (appTheme.isLightModeEnabled()) {
            binding.container.setBackgroundResource(R.drawable.gradient_light)
        } else {
            binding.container.setBackgroundResource(R.drawable.gradient_dark)
        }
    }

    private fun setupListeners() {
        binding.appleButton.setOnClickListener {
            globalActivityStarter.start(this, MacOsScreenWithEmptyParams)
        }

        binding.windowsButton.setOnClickListener {
            globalActivityStarter.start(this, WindowsScreenWithEmptyParams)
        }
    }

    private fun setupInternalToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setTitle(null)
        toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
    }

    companion object {
        data object PirScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
