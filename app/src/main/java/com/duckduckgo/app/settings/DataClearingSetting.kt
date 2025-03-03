/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingDataClearingBinding
import com.duckduckgo.app.firebutton.FireButtonScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_FIRE_BUTTON_PRESSED
import com.duckduckgo.app.settings.DataClearingSettingViewModel.Command
import com.duckduckgo.app.settings.DataClearingSettingViewModel.Command.LaunchDataClearingScreen
import com.duckduckgo.app.settings.DataClearingSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(307)
class DataClearingSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return DataClearingSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
                "data", "clearing", "history", "cache", "fire", "fire button", "fireproof",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class DataClearingSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, DataClearingSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): DataClearingSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[DataClearingSettingViewModel::class.java]
    }

    private val binding: ContentSettingDataClearingBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.fireButtonSetting.setOnClickListener {
            viewModel.onDataClearingSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.fireButtonSetting) {
            visibility = if (viewState.showDataClearingSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchDataClearingScreen -> {
                globalActivityStarter.start(context, FireButtonScreenNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class DataClearingSettingViewModel @Inject constructor(
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showDataClearingSetting = false,
        )
    }

    fun onDataClearingSettingClicked() {
        _commands.trySend(LaunchDataClearingScreen)
        pixel.fire(SETTINGS_FIRE_BUTTON_PRESSED)
    }

    data class ViewState(
        val showDataClearingSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchDataClearingScreen : Command()
    }
}
