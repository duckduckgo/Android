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

package com.duckduckgo.app.dev.settings.tabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityNewTabTransitionDevSettingsBinding
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionConfig
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionDevStore
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class NewTabTransitionDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var devStore: NewTabTransitionDevStore

    @Inject
    lateinit var tabManagerFeatureFlags: TabManagerFeatureFlags

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityNewTabTransitionDevSettingsBinding by viewBinding()

    private var config: NewTabTransitionConfig
        get() = devStore.config
        set(value) {
            devStore.config = value
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)

        binding.browserOriginSelector.setOnClickListener { pickOrigin(binding.browserOriginSelector) { config = config.copy(browserOrigin = it) } }
        binding.tabManagerOriginSelector.setOnClickListener {
            pickOrigin(binding.tabManagerOriginSelector) { config = config.copy(tabManagerOrigin = it) }
        }
        binding.belowCenterSettings.setOnClickListener { openOriginSettings(NewTabOrigin.BELOW_CENTER) }
        binding.rightOfCenterSettings.setOnClickListener { openOriginSettings(NewTabOrigin.RIGHT_OF_CENTER) }
        binding.resetButton.setOnClickListener { config = NewTabTransitionConfig.DEFAULT }
        render()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val flagEnabled = withContext(dispatchers.io()) { tabManagerFeatureFlags.newTabTransitions().isEnabled() }
            binding.flagStatus.setSecondaryText(
                getString(if (flagEnabled) R.string.newTabTransitionDevSettingsFlagOn else R.string.newTabTransitionDevSettingsFlagOff),
            )
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentLayout, drawBehindGestureNav = false)
    }

    private fun render() {
        val config = config
        binding.browserOriginSelector.setSecondaryText(getString(config.browserOrigin.label()))
        binding.tabManagerOriginSelector.setSecondaryText(getString(config.tabManagerOrigin.label()))
        binding.slowMotionToggle.quietlySetIsChecked(config.slowMotion) { _, isChecked -> this.config = this.config.copy(slowMotion = isChecked) }
    }

    private fun pickOrigin(
        anchor: TwoLineListItem,
        onSelected: (NewTabOrigin) -> Unit,
    ) {
        val popup = PopupMenu(layoutInflater, R.layout.popup_window_new_tab_origin)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.belowCenter)) { onSelected(NewTabOrigin.BELOW_CENTER) }
            onMenuItemClicked(view.findViewById(R.id.rightOfCenter)) { onSelected(NewTabOrigin.RIGHT_OF_CENTER) }
        }
        popup.show(binding.root, anchor)
    }

    private fun openOriginSettings(origin: NewTabOrigin) {
        startActivity(NewTabTransitionOriginDevSettingsActivity.intent(this, origin))
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NewTabTransitionDevSettingsActivity::class.java)
        }
    }
}
