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

package com.duckduckgo.app.browser.favicon.setting

import android.content.*
import android.util.*
import android.widget.*
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.app.browser.databinding.ViewSyncFaviconsFetchingBinding
import com.duckduckgo.app.browser.favicon.setting.FaviconFetchingViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.*
import com.duckduckgo.saved.sites.impl.databinding.*
import dagger.android.support.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.*

@InjectWith(ViewScope::class)
class FaviconFetchingSyncSetting @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: FaviconFetchingViewModel.Factory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private var job: ConflatedJob = ConflatedJob()

    private val binding: ViewSyncFaviconsFetchingBinding by viewBinding()

    private val viewModel: FaviconFetchingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[FaviconFetchingViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.syncFaviconsFetching.setOnCheckedChangeListener { buttonView, isChecked ->
            viewModel.onFaviconFetchingSettingChanged(isChecked)
        }

        job += viewModel.viewState()
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    private fun render(it: ViewState) {
        binding.syncFaviconsFetching.setIsChecked(it.faviconsFetchingEnabled)
    }
}
