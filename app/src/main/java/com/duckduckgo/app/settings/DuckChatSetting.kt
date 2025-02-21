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
import com.duckduckgo.app.browser.databinding.ViewSettingsItemDuckChatBinding
import com.duckduckgo.app.settings.DuckChatSettingViewModel.Command
import com.duckduckgo.app.settings.DuckChatSettingViewModel.Command.LaunchDuckChatScreen
import com.duckduckgo.app.settings.DuckChatSettingViewModel.ViewState
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(308)
class DuckChatSettingNode @Inject constructor() : RootSettingsNode {
    override val categoryNameResId = R.string.settingsHeadingMainSettings
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return DuckChatSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "duck", "chat", "ai", "llm", "assistant", "intelligence", "artificial",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class DuckChatSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, DuckChatSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): DuckChatSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[DuckChatSettingViewModel::class.java]
    }

    private val binding: ViewSettingsItemDuckChatBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.duckChatSetting.setOnClickListener {
            viewModel.onDuckChatSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.duckChatSetting) {
            visibility = if (viewState.showDuckChatSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchDuckChatScreen -> {
                globalActivityStarter.start(context, DuckChatSettingsNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class DuckChatSettingViewModel @Inject constructor() : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showDuckChatSetting = false,
        )
    }

    fun onDuckChatSettingClicked() {
        _commands.trySend(LaunchDuckChatScreen)
    }

    data class ViewState(
        val showDuckChatSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchDuckChatScreen : Command()
    }
}
