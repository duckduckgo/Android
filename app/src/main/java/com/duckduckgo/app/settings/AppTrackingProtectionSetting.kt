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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingAppTrackingProtectionBinding
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_APPTP_PRESSED
import com.duckduckgo.app.settings.AppTrackingProtectionSettingViewModel.Command
import com.duckduckgo.app.settings.AppTrackingProtectionSettingViewModel.Command.LaunchAppTrackingProtectionOnboarding
import com.duckduckgo.app.settings.AppTrackingProtectionSettingViewModel.Command.LaunchAppTrackingProtectionScreen
import com.duckduckgo.app.settings.AppTrackingProtectionSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(106)
class AppTrackingProtectionSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Protections
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return AppTrackingProtectionSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "app", "tracking", "protection",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class AppTrackingProtectionSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, AppTrackingProtectionSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): AppTrackingProtectionSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[AppTrackingProtectionSettingViewModel::class.java]
    }

    private val binding: ContentSettingAppTrackingProtectionBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.appTrackingProtectionSetting.setOnClickListener {
            viewModel.onAppTrackingProtectionSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.appTrackingProtectionSetting) {
            visibility = if (viewState.showAppTrackingProtectionSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setStatus(viewState.isAppTrackingProtectionEnabled)
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchAppTrackingProtectionScreen -> {
                globalActivityStarter.start(context, AppTrackerActivityWithEmptyParams)
            }

            LaunchAppTrackingProtectionOnboarding -> {
                globalActivityStarter.start(context, AppTrackerOnboardingActivityWithEmptyParamsParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class AppTrackingProtectionSettingViewModel @Inject constructor(
    private val appTrackingProtection: AppTrackingProtection,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    private val appTPPollJob = ConflatedJob()

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showAppTrackingProtectionSetting = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch {
            _viewState.update {
                it.copy(
                    isAppTrackingProtectionEnabled = appTrackingProtection.isRunning(),
                )
            }
        }
        startPollingAppTPState()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        appTPPollJob.cancel()
    }

    // FIXME
    // We need to fix this. This logic as inside the start method but it messes with the unit tests
    // because when doing runningBlockingTest {} there is no delay and the tests crashes because this
    // becomes a while(true) without any delay
    private fun startPollingAppTPState() {
        appTPPollJob += viewModelScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                _viewState.update {
                    it.copy(
                        isAppTrackingProtectionEnabled = appTrackingProtection.isRunning(),
                    )
                }
                delay(1_000)
            }
        }
    }

    fun onAppTrackingProtectionSettingClicked() {
        viewModelScope.launch {
            if (appTrackingProtection.isOnboarded()) {
                _commands.trySend(LaunchAppTrackingProtectionScreen)
            } else {
                _commands.trySend(LaunchAppTrackingProtectionOnboarding)
            }
            pixel.fire(SETTINGS_APPTP_PRESSED)
        }
    }

    data class ViewState(
        val showAppTrackingProtectionSetting: Boolean = true,
        val isAppTrackingProtectionEnabled: Boolean = false,
    )

    sealed class Command {
        data object LaunchAppTrackingProtectionScreen : Command()
        data object LaunchAppTrackingProtectionOnboarding : Command()
    }
}
