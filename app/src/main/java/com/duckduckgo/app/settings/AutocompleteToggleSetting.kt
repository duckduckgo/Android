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
import android.widget.CompoundButton
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingPrivateSearchAutocompleteBinding
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_TOGGLED_ON
import com.duckduckgo.app.privatesearch.PrivateSearchNestedSettingNode
import com.duckduckgo.app.settings.PrivateSearchAutocompleteSettingViewModel.Command
import com.duckduckgo.app.settings.PrivateSearchAutocompleteSettingViewModel.ViewState
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.history.api.NavigationHistory
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PrivateSearchNestedSettingNode::class,
)
@PriorityKey(0)
class PrivateSearchAutocompleteSettingNode @Inject constructor() : PrivateSearchNestedSettingNode {
    override val categoryNameResId = R.string.settingsHeadingProtections
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return PrivateSearchAutocompleteSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf("search", "suggestions", "bookmark", "complete", "autocomplete", "autofill")
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class PrivateSearchAutocompleteSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, PrivateSearchAutocompleteSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): PrivateSearchAutocompleteSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PrivateSearchAutocompleteSettingViewModel::class.java]
    }

    private val binding: ContentSettingPrivateSearchAutocompleteBinding by viewBinding()

    private val autocompleteToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.privateSearchAutocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
    }

    override fun renderView(viewState: ViewState) {
        binding.privateSearchAutocompleteToggle.quietlySetIsChecked(
            newCheckedState = viewState.autoCompleteSuggestionsEnabled,
            changeListener = autocompleteToggleListener,
        )
        with(binding.privateSearchAutocompleteToggle) {
            visibility = if (viewState.visible) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class PrivateSearchAutocompleteSettingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val history: NavigationHistory,
    private val dispatcherProvider: DispatcherProvider,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            visible = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        /*
         * Update on each start to reflect state changes from nested activities.
         * `settingsDataStore` is not observable, requiring manual management.
         */
        viewModelScope.launch(dispatcherProvider.io()) {
            val autoCompleteEnabled = settingsDataStore.autoCompleteSuggestionsEnabled
            if (!autoCompleteEnabled) {
                history.setHistoryUserEnabled(false)
            }
            _viewState.value = ViewState(
                autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
            )
        }
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.autoCompleteSuggestionsEnabled = enabled
            if (!enabled) {
                history.setHistoryUserEnabled(false)
            }
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_TOGGLED_OFF)
            }
            _viewState.update {
                it.copy(autoCompleteSuggestionsEnabled = enabled)
            }
        }
    }

    data class ViewState(
        val visible: Boolean = true,
        val autoCompleteSuggestionsEnabled: Boolean = false,
    )

    sealed class Command {
    }
}
