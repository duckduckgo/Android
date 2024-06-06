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

package com.duckduckgo.newtabpage.impl.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.NewTabSettingsScreenNoParams
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabPageBinding
import com.duckduckgo.newtabpage.impl.view.NewTabPageViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ViewScope::class)
class NewTabPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewNewTabPageBinding by viewBinding()

    private val viewModel: NewTabPageViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewTabPageViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        binding.newTabEdit.setOnClickListener {
            globalActivityStarter.start(context, NewTabSettingsScreenNoParams)
        }

        binding.newTabSectionsContent.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(
                p0: View?,
                p1: View?,
            ) {
                logcat { "New Tab: view added child count ${binding.newTabSectionsContent.childCount}" }
            }

            override fun onChildViewRemoved(
                p0: View?,
                p1: View?,
            ) {
                val childCount = binding.newTabSectionsContent.childCount
                logcat { "New Tab: view removed child count $childCount" }
                if (childCount == 0) {
                    viewModel.refreshViews()
                }
            }
        },
        )
    }

    private fun render(viewState: ViewState) {
        logcat { "New Tab: render $$viewState" }
        if (viewState.loading) {
            binding.newTabContentShimmer.startShimmer()
        } else {
            if (viewState.sections.isEmpty()) {
                binding.newTabContentShimmer.gone()
                binding.newTabSectionsContent.gone()
                binding.ddgLogo.show()
            } else {
                // remove all views but the RMF
                val childCount = binding.newTabSectionsContent.childCount
                if (childCount > 0) {
                    binding.newTabSectionsContent.removeViews(1, childCount - 1)
                }
                viewState.sections.onEach {
                    binding.newTabSectionsContent.addView(
                        it.getView(context),
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }

                binding.ddgLogo.gone()
                binding.newTabContentShimmer.gone()
                binding.newTabSectionsContent.show()
            }

            if (binding.newTabContentShimmer.isVisible) {
                binding.newTabContentShimmer.stopShimmer()
                binding.newTabContentShimmer.gone()
                binding.ddgLogo.gone()
                binding.newTabSectionsContent.show()
            }

            logcat { "New Tab: child count ${binding.newTabContentShimmer.childCount}" }
        }
    }
}
