/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.sync.impl.databinding.ViewSyncErrorWarningBinding
import com.duckduckgo.sync.impl.ui.SyncErrorViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SyncErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewSyncErrorWarningBinding by viewBinding()

    private val viewModel: SyncErrorViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncErrorViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState()
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedJob.cancel()
    }

    private fun render(viewState: ViewState) {
        this.isVisible = viewState.message != null
        val message = viewState.message ?: return
        binding.syncFailedWarning.setText(context.getString(message))
    }
}
