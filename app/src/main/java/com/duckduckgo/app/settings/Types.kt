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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.databinding.ContentSettingsDefaultBrowserBinding
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_DEFAULT_BROWSER_PRESSED
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.Command
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.settings.SetAsDefaultBrowserSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.RootSettingsNode
import com.duckduckgo.common.ui.SettingsNode
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(0)
class SetAsDefaultBrowserSettingNode @Inject constructor() : RootSettingsNode {
    override val parent: SettingsNode? = null
    override val children: List<SettingsNode> = emptyList()

    override fun getView(context: Context): View {
        return SetAsDefaultBrowserSettingNodeView(context)
    }

    override val id: UUID = UUID.randomUUID()

    override fun generateKeywords(): Set<String> {
        return setOf("browser", "default")
    }
}

@InjectWith(ViewScope::class)
class SetAsDefaultBrowserSettingNodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel: SetAsDefaultBrowserSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SetAsDefaultBrowserSettingViewModel::class.java]
    }

    private val binding: ContentSettingsDefaultBrowserBinding by viewBinding()

    private var job: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val viewTreeLifecycleOwner = findViewTreeLifecycleOwner()!!
        viewTreeLifecycleOwner.lifecycle.addObserver(viewModel)
        val coroutineScope = viewTreeLifecycleOwner.lifecycleScope

        job += viewModel.commands
            .onEach { processCommands(it) }
            .launchIn(coroutineScope)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope)

        binding.setAsDefaultBrowserSetting.setOnClickListener {
            viewModel.onDefaultBrowserSettingClicked()
        }
    }

    private fun renderView(viewState: ViewState) {
        with(binding.setAsDefaultBrowserSetting) {
            visibility = if (viewState.showDefaultBrowserSetting) {
                setStatus(isOn = viewState.isAppDefaultBrowser)
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            LaunchDefaultBrowser -> {
                context.launchDefaultAppActivity()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }
}

@ContributesViewModel(ViewScope::class)
class SetAsDefaultBrowserSettingViewModel @Inject constructor(
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver {

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

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

