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

package com.duckduckgo.adblocking.impl.ui

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Disabled
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Enabled
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.databinding.ActivityAdBlockingSettingsV2Binding
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AdBlockingSettingsV2NoParams::class)
class AdBlockingSettingsV2Activity : BaseAdBlockingSettingsActivity() {

    private val binding: ActivityAdBlockingSettingsV2Binding by viewBinding()

    override val toolbar: Toolbar get() = binding.includeToolbar.toolbar
    override val blockAdsToggle: OneLineListItem get() = binding.blockAdsToggle
    override val adBlockingDescription: DaxTextView get() = binding.adBlockingDescription
    override val duckPlayerEntry: DaxListItem get() = binding.duckPlayerEntry
    override val duckPlayerDescription: DaxTextView get() = binding.duckPlayerDescription

    override val learnMoreScreenTitle: Int = R.string.ad_blocking_settings_title_v2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setTitle(R.string.ad_blocking_settings_title_v2)
        configure()
    }

    override fun render(state: AdBlockingSettingsViewModel.ViewState) {
        super.render(state)
        binding.adBlockingStatusIndicator.setStatus(state.isEnabled)
        binding.duckPlayerEntry.setSecondaryText(
            when (state.duckPlayerMode) {
                Enabled -> getString(R.string.duck_player_mode_always)
                Disabled -> getString(R.string.duck_player_mode_never)
                else -> getString(R.string.duck_player_mode_always_ask)
            },
        )
        if (state.isContingencyMode) {
            binding.blockAdsToggle.gone()
            binding.adBlockingDescription.gone()
            binding.contingencyModeItem.show()
        } else {
            binding.contingencyModeItem.gone()
            binding.blockAdsToggle.show()
        }
    }
}
