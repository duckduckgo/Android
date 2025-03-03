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
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.appearance.AppearanceThemeSettingViewModel.Command
import com.duckduckgo.app.appearance.AppearanceThemeSettingViewModel.Command.LaunchThemeSettings
import com.duckduckgo.app.appearance.AppearanceThemeSettingViewModel.Command.UpdateTheme
import com.duckduckgo.app.appearance.AppearanceThemeSettingViewModel.ViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingAppearanceThemeBinding
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_DARK
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT
import com.duckduckgo.app.settings.getActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.common.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.common.ui.sendThemeChangedBroadcast
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = AppearanceNestedSettingNode::class,
)
@PriorityKey(0)
class AppearanceThemeSettingNode @Inject constructor() : AppearanceNestedSettingNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return AppearanceThemeSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "theme", "dark", "light", "night", "day",
            "color", "palette", "mode",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class AppearanceThemeSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, AppearanceThemeSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): AppearanceThemeSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[AppearanceThemeSettingViewModel::class.java]
    }

    private val binding: ContentSettingAppearanceThemeBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.selectedThemeSetting.setClickListener { viewModel.userRequestedToChangeTheme() }
    }

    override fun renderView(viewState: ViewState) {
        updateSelectedTheme(viewState.theme)
    }

    override fun processCommands(command: Command) {
        super.processCommands(command)
        when (command) {
            is LaunchThemeSettings -> launchThemeSelector(command.theme)
            is UpdateTheme -> context.getActivity()?.sendThemeChangedBroadcast()
        }
    }

    private fun updateSelectedTheme(selectedTheme: DuckDuckGoTheme) {
        val subtitle = context.getString(
            when (selectedTheme) {
                DARK -> R.string.settingsDarkTheme
                LIGHT -> R.string.settingsLightTheme
                SYSTEM_DEFAULT -> R.string.settingsSystemTheme
            },
        )
        binding.selectedThemeSetting.setSecondaryText(subtitle)
    }

    private fun launchThemeSelector(theme: DuckDuckGoTheme) {
        val currentTheme = theme.getOptionIndex()
        RadioListAlertDialogBuilder(context)
            .setTitle(R.string.settingsTheme)
            .setOptions(
                listOf(
                    R.string.settingsSystemTheme,
                    R.string.settingsLightTheme,
                    R.string.settingsDarkTheme,
                ),
                currentTheme,
            )
            .setPositiveButton(R.string.settingsThemeDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedTheme = when (selectedItem) {
                            2 -> DuckDuckGoTheme.LIGHT
                            3 -> DuckDuckGoTheme.DARK
                            else -> DuckDuckGoTheme.SYSTEM_DEFAULT
                        }
                        viewModel.onThemeSelected(selectedTheme)
                    }
                },
            )
            .show()
    }
}

@ContributesViewModel(ViewScope::class)
class AppearanceThemeSettingViewModel @Inject constructor(
    private val themingDataStore: ThemingDataStore,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            visible = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatcherProvider.io()) {
            _viewState.value = ViewState(
                theme = themingDataStore.theme,
            )
        }
    }

    fun userRequestedToChangeTheme() {
        _commands.trySend(Command.LaunchThemeSettings(viewState.value.theme))
        pixel.fire(AppPixelName.SETTINGS_THEME_OPENED)
    }

    fun onThemeSelected(selectedTheme: DuckDuckGoTheme) {
        Timber.d("User toggled theme, theme to set: $selectedTheme")
        if (themingDataStore.isCurrentlySelected(selectedTheme)) {
            Timber.d("User selected same theme they've already set: $selectedTheme; no need to do anything else")
            return
        }
        viewModelScope.launch(dispatcherProvider.io()) {
            themingDataStore.theme = selectedTheme
            withContext(dispatcherProvider.main()) {
                _viewState.update { it.copy(theme = selectedTheme) }
                _commands.trySend(Command.UpdateTheme)
            }
        }

        val pixelName =
            when (selectedTheme) {
                LIGHT -> SETTINGS_THEME_TOGGLED_LIGHT
                DARK -> SETTINGS_THEME_TOGGLED_DARK
                SYSTEM_DEFAULT -> SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT
            }
        pixel.fire(pixelName)
    }

    data class ViewState(
        val visible: Boolean = true,
        val theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT,
    )

    sealed class Command {
        data class LaunchThemeSettings(val theme: DuckDuckGoTheme) : Command()
        data object UpdateTheme : Command()
    }
}
