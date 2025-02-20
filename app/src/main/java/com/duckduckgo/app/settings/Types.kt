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
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingsDefaultBrowserBinding
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_DEFAULT_BROWSER_PRESSED
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.Command
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.RootSettingsNode
import com.duckduckgo.common.ui.SearchStatus
import com.duckduckgo.common.ui.SearchStatus.MISS
import com.duckduckgo.common.ui.Searchable
import com.duckduckgo.common.ui.SettingsNode
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(1)
class SetAsDefaultBrowserSettingNode @Inject constructor() : RootSettingsNode {
    override val parent: SettingsNode? = null
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return SetAsDefaultBrowserSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf("browser", "default")
    }
}

// @ContributesMultibinding(scope = ActivityScope::class)
// @PriorityKey(0)
class TitleProtectionsSettingNode @Inject constructor() : RootSettingsNode {
    override val parent: SettingsNode? = null
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return SectionHeaderListItem(context).apply { setText(R.string.settingsHeadingProtections) }
    }

    override fun generateKeywords(): Set<String> {
        return setOf("browser", "default")
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class SetAsDefaultBrowserSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, SetAsDefaultBrowserSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): SetAsDefaultBrowserSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SetAsDefaultBrowserSettingViewModel::class.java]
    }

    private val binding: ContentSettingsDefaultBrowserBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.setAsDefaultBrowserSetting.setOnClickListener {
            viewModel.onDefaultBrowserSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.setAsDefaultBrowserSetting) {
            visibility = if (viewState.showDefaultBrowserSetting) {
                setStatus(isOn = viewState.isAppDefaultBrowser)
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchDefaultBrowser -> {
                context.launchDefaultAppActivity()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
abstract class SettingNodeView<C, VS, VM : SettingViewModel<C, VS>>(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    override val searchableId: UUID,
) : FrameLayout(context, attrs, defStyle), Searchable {

    protected val viewModel: VM by lazy {
        provideViewModel()
    }

    protected abstract fun provideViewModel(): VM

    private var conflatedCommandsJob: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val viewTreeLifecycleOwner = findViewTreeLifecycleOwner()!!
        viewTreeLifecycleOwner.lifecycle.addObserver(viewModel)
        val coroutineScope = viewTreeLifecycleOwner.lifecycleScope

        conflatedCommandsJob += viewModel.commands
            .onEach { processCommands(it) }
            .launchIn(coroutineScope)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedCommandsJob.cancel()
        conflatedStateJob.cancel()
    }

    final override fun setSearchStatus(status: SearchStatus) {
        viewModel.setSearchStatus(status)
    }

    protected abstract fun renderView(viewState: VS)

    protected abstract fun processCommands(command: C)
}

@ContributesViewModel(ViewScope::class)
class SetAsDefaultBrowserSettingViewModel @Inject constructor(
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        val defaultBrowserCapabilitySupported = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration()
        val defaultBrowserAlready = defaultWebBrowserCapability.isDefaultBrowser()
        _viewState.update {
            it.copy(
                showDefaultBrowserSetting = defaultBrowserCapabilitySupported,
                isAppDefaultBrowser = defaultBrowserAlready,
            )
        }
    }

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showDefaultBrowserSetting = false,
        )
    }

    fun onDefaultBrowserSettingClicked() {
        _commands.trySend(LaunchDefaultBrowser)
        pixel.fire(SETTINGS_DEFAULT_BROWSER_PRESSED)
    }

    data class ViewState(
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
    )

    sealed class Command {
        data object LaunchDefaultBrowser : Command()
    }
}

abstract class SettingViewModel<C, V>(defaultViewState: V) : ViewModel(), DefaultLifecycleObserver {
    protected val _commands = Channel<C>(capacity = Channel.CONFLATED)
    val commands: Flow<C> = _commands.receiveAsFlow()

    private val searchStatus = MutableStateFlow(SearchStatus.NONE)
    protected val _viewState = MutableStateFlow(defaultViewState)
    val viewState = _viewState.combine(searchStatus) { viewState, searchStatus ->
        if (searchStatus == MISS) {
            getSearchMissViewState()
        } else {
            viewState
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultViewState)

    abstract fun getSearchMissViewState(): V

    fun setSearchStatus(status: SearchStatus) {
        searchStatus.value = status
    }
}

