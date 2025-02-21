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
import com.duckduckgo.app.browser.databinding.ContentSettingsDuckPlayerBinding
import com.duckduckgo.app.settings.DuckPlayerSettingViewModel.Command
import com.duckduckgo.app.settings.DuckPlayerSettingViewModel.Command.LaunchDuckPlayerScreen
import com.duckduckgo.app.settings.DuckPlayerSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsNoParams
import com.duckduckgo.duckplayer.impl.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(309)
class DuckPlayerSetting @Inject constructor() : RootSettingsNode {
    override val categoryNameResId = R.string.settingsHeadingMainSettings
    override val children: List<SettingsNode> = emptyList()
    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return DuckPlayerSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "duck", "player", "duck player", "youtube", "videos", "privacy",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class DuckPlayerSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, DuckPlayerSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): DuckPlayerSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[DuckPlayerSettingViewModel::class.java]
    }

    private val binding: ContentSettingsDuckPlayerBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.duckPlayerSetting.setOnClickListener {
            viewModel.onDuckPlayerSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.duckPlayerSetting) {
            visibility = if (viewState.showDuckPlayerSetting) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchDuckPlayerScreen -> {
                globalActivityStarter.start(context, DuckPlayerSettingsNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class DuckPlayerSettingViewModel @Inject constructor(
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showDuckPlayerSetting = false,
        )
    }

    fun onDuckPlayerSettingClicked() {
        _commands.trySend(LaunchDuckPlayerScreen)
        pixel.fire(DUCK_PLAYER_SETTINGS_PRESSED)
    }

    data class ViewState(
        val showDuckPlayerSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchDuckPlayerScreen : Command()
    }
}

