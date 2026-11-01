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

package com.duckduckgo.pir.impl.desktop

import android.os.Bundle
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos.api.MacOsScreenWithEmptyParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.pir.api.PirScreens.PirDesktopScreen
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.databinding.ActivityPirDesktopBinding
import com.duckduckgo.windows.api.ui.WindowsScreenWithEmptyParams
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDesktopScreen::class)
class PirDesktopActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityPirDesktopBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupInternalToolbar()
        setupListeners()
        if (appTheme.isLightModeEnabled()) {
            binding.container.setBackgroundResource(R.drawable.gradient_light)
        } else {
            binding.container.setBackgroundResource(R.drawable.gradient_dark)
        }
        configureEdgeToEdgeInsets()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = false)
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
}
