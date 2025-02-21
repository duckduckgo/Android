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
import com.duckduckgo.app.browser.databinding.ContentSettingsPrivateSearchBinding
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_PRIVATE_SEARCH_PRESSED
import com.duckduckgo.app.privatesearch.PrivateSearchNestedSettingNode
import com.duckduckgo.app.privatesearch.PrivateSearchScreenNoParams
import com.duckduckgo.app.settings.PrivateSearchSettingViewModel.Command
import com.duckduckgo.app.settings.PrivateSearchSettingViewModel.Command.LaunchPrivateSearchWebPage
import com.duckduckgo.app.settings.PrivateSearchSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(2)
class PrivateSearchSettingNode @Inject constructor() : RootSettingsNode {
    override val categoryNameResId = R.string.settingsHeadingProtections

    @Inject
    lateinit var _nestedSettingsPlugins: PluginPoint<PrivateSearchNestedSettingNode>
    override val children by lazy {
        _nestedSettingsPlugins.getPlugins()
    }

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return PrivateSearchSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "private", "search", "privacy", "trackers", "tracking", "spy",
            "suggestions", "bookmark", "complete", "autocomplete", "autofill"
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class PrivateSearchSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, PrivateSearchSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): PrivateSearchSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PrivateSearchSettingViewModel::class.java]
    }

    private val binding: ContentSettingsPrivateSearchBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.privateSearchSetting.setOnClickListener {
            viewModel.onPrivateSearchSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.privateSearchSetting) {
            visibility = if (viewState.showPrivateSearchSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchPrivateSearchWebPage -> {
                globalActivityStarter.start(context, PrivateSearchScreenNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class PrivateSearchSettingViewModel @Inject constructor(
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showPrivateSearchSetting = false,
        )
    }

    fun onPrivateSearchSettingClicked() {
        _commands.trySend(LaunchPrivateSearchWebPage)
        pixel.fire(SETTINGS_PRIVATE_SEARCH_PRESSED)
    }

    data class ViewState(
        val showPrivateSearchSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchPrivateSearchWebPage : Command()
    }
}
