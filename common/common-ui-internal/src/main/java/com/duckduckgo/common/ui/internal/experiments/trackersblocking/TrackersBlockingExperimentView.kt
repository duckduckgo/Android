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

package com.duckduckgo.common.ui.internal.experiments.trackersblocking

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.internal.databinding.ViewTrackersBlockingExperimentBinding
import com.duckduckgo.common.ui.internal.experiments.trackersblocking.TrackersBlockingExperimentViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class TrackersBlockingExperimentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewTrackersBlockingExperimentBinding by viewBinding()

    private val viewModel: TrackersBlockingExperimentViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[TrackersBlockingExperimentViewModel::class.java]
    }

    private val trackersBlockingVariant1ToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onTrackersBlockingVariant1ExperimentalUIModeChanged(isChecked)
    }

    private val trackersBlockingVariant2ToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onTrackersBlockingVariant2ExperimentalUIModeChanged(isChecked)
    }

    private val trackersBlockingVariant3ToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onTrackersBlockingVariant3ExperimentalUIModeChanged(isChecked)
    }

    private val trackersBlockingVariant4ToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onTrackersBlockingVariant4ExperimentalUIModeChanged(isChecked)
    }

    private val trackersBlockingVariant5ToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onTrackersBlockingVariant5ExperimentalUIModeChanged(isChecked)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        viewModel.viewState()
            .onEach { render(it) }
            .launchIn(coroutineScope!!)
    }

    private fun render(viewState: ViewState) {
        binding.trackersBlockingVariant1ExperimentalUIMode.quietlySetIsChecked(viewState.variant1, trackersBlockingVariant1ToggleListener)
        binding.trackersBlockingVariant2ExperimentalUIMode.quietlySetIsChecked(viewState.variant2, trackersBlockingVariant2ToggleListener)
        binding.trackersBlockingVariant3ExperimentalUIMode.quietlySetIsChecked(viewState.variant3, trackersBlockingVariant3ToggleListener)
        binding.trackersBlockingVariant4ExperimentalUIMode.quietlySetIsChecked(viewState.variant4, trackersBlockingVariant4ToggleListener)
        binding.trackersBlockingVariant5ExperimentalUIMode.quietlySetIsChecked(viewState.variant5, trackersBlockingVariant5ToggleListener)

        Snackbar.make(binding.root, "Updated", Snackbar.LENGTH_SHORT).show()
    }
}
