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

package com.duckduckgo.app.appearance

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.appearance.AppearanceSettingViewModel.Command
import com.duckduckgo.app.appearance.AppearanceSettingViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.appearance.AppearanceSettingViewModel.ViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingAppearanceBinding
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_APPEARANCE_PRESSED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

interface AppearanceNestedSettingNode : SettingsNode

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = AppearanceNestedSettingNode::class,
)
@Suppress("unused")
interface AppearanceNestedSettingNodePluginPoint

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(303)
class AppearanceSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other

    @Inject
    lateinit var _nestedSettingsPlugins: PluginPoint<AppearanceNestedSettingNode>
    override val children by lazy {
        _nestedSettingsPlugins.getPlugins()
    }

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return AppearanceSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
                "theme", "dark", "light", "night", "day",
                "color", "palette", "UI", "interface",
                "customization", "scheme", "appearance",
                "text", "font", "mode", "style", "layout", "design",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class AppearanceSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, AppearanceSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): AppearanceSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[AppearanceSettingViewModel::class.java]
    }

    private val binding: ContentSettingAppearanceBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.appearanceSetting.setOnClickListener {
            viewModel.onAppearanceSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.appearanceSetting) {
            visibility = if (viewState.showAppearanceSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchAppearanceScreen -> {
                globalActivityStarter.start(context, AppearanceScreen.Default)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class AppearanceSettingViewModel @Inject constructor(
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showAppearanceSetting = false,
        )
    }

    fun onAppearanceSettingClicked() {
        _commands.trySend(LaunchAppearanceScreen)
        pixel.fire(SETTINGS_APPEARANCE_PRESSED)
    }

    data class ViewState(
        val showAppearanceSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchAppearanceScreen : Command()
    }
}
