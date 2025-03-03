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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingCookieProtectionBinding
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED
import com.duckduckgo.app.settings.CookieProtectionSettingViewModel.Command
import com.duckduckgo.app.settings.CookieProtectionSettingViewModel.Command.LaunchCookiePopupProtectionScreen
import com.duckduckgo.app.settings.CookieProtectionSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
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
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(105)
class CookieProtectionSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Protections
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return CookieProtectionSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
                "cookie", "protection", "pop-up",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class CookieProtectionSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, CookieProtectionSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): CookieProtectionSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[CookieProtectionSettingViewModel::class.java]
    }

    private val binding: ContentSettingCookieProtectionBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.cookieProtectionSetting.setOnClickListener {
            viewModel.onCookieProtectionSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.cookieProtectionSetting) {
            visibility = if (viewState.showCookieProtectionSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setStatus(viewState.isAutoconsentEnabled)
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchCookiePopupProtectionScreen -> {
                context.startActivity(AutoconsentSettingsActivity.intent(context))
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class CookieProtectionSettingViewModel @Inject constructor(
    private val autoconsent: Autoconsent,
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showCookieProtectionSetting = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        _viewState.update {
            it.copy(
                isAutoconsentEnabled = autoconsent.isSettingEnabled()
            )
        }
    }

    fun onCookieProtectionSettingClicked() {
        _commands.trySend(LaunchCookiePopupProtectionScreen)
        pixel.fire(SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED)
    }

    data class ViewState(
        val showCookieProtectionSetting: Boolean = true,
        val isAutoconsentEnabled: Boolean = false,
    )

    sealed class Command {
        data object LaunchCookiePopupProtectionScreen : Command()
    }
}
