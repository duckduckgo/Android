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
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.NewTabSettingsScreenNoParams
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.impl.R
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
            viewModel.onCustomizePagePressed()
            globalActivityStarter.start(context, NewTabSettingsScreenNoParams)
        }
    }

    private fun render(viewState: ViewState) {
        logcat { "New Tab: render $$viewState" }
        if (viewState.loading) {
            binding.newTabContentShimmer.startShimmer()
        } else {
            if (viewState.showDax) {
                binding.ddgLogo.show()
            } else {
                binding.ddgLogo.gone()
            }

            if (viewState.showWelcome) {
                val message = Message(
                    title = context.getString(R.string.newTabPageWelcomeTitle),
                    subtitle = context.getString(R.string.newTabPageWelcomeMessage),
                )
                binding.newTabWelcomeContent.apply {
                    setMessage(message)
                    onCloseButtonClicked {
                        viewModel.onWelcomeMessageCleared()
                        gone()
                    }
                }
                binding.newTabWelcomeContent.show()
            } else {
                binding.newTabWelcomeContent.gone()
            }

            if (viewState.sections.isEmpty()) {
                binding.newTabContentShimmer.gone()
                binding.newTabSectionsContent.gone()
            } else {
                // we only want to make changes if the sections have changed
                val existingSections = binding.newTabSectionsContent.children.map { it.tag }.toMutableList()
                val newSections = viewState.sections.map { it.name }
                if (existingSections != newSections) {
                    // RMF is a special case, we don't want to remove it.
                    // We can only show that message once, so removing the view and adding it again won't work
                    val rmfView = binding.newTabSectionsContent.findViewWithTag<View>(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                    if (rmfView != null) {
                        binding.newTabSectionsContent.removeViews(1, binding.newTabSectionsContent.childCount - 1)
                    } else {
                        binding.newTabSectionsContent.removeAllViews()
                    }
                }

                // we will only add sections that haven't been added yet
                viewState.sections.onEach { section ->
                    val sectionView = binding.newTabSectionsContent.findViewWithTag<View>(section.name)
                    if (sectionView == null) {
                        binding.newTabSectionsContent.addView(
                            section.getView(context).also { it?.tag = section.name },
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                }

                binding.newTabContentShimmer.gone()
                binding.newTabSectionsContent.show()
            }
        }
    }
}
