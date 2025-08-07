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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.NewTabSettingsScreenNoParams
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabPageBinding
import com.duckduckgo.newtabpage.impl.view.NewTabPageViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ViewScope::class)
class NewTabPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    private val showLogo: Boolean = true,
    private val onHasContent: ((Boolean) -> Unit)? = null,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewNewTabPageBinding by viewBinding()

    private val viewModel: NewTabPageViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewTabPageViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        setClickListeners()
        setAnimationListeners()
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        logcat { "New Tab Render: loading: ${viewState.loading} showDax: ${viewState.showDax} sections: ${viewState.sections.size}" }
        if (viewState.loading) {
            binding.newTabContentShimmer.startShimmer()
        } else {
            if (!showLogo && viewState.showDax) {
                this.gone()
                onHasContent?.invoke(false)
            } else {
                this.show()
                onHasContent?.invoke(true)
                if (viewState.showDax) {
                    binding.ddgLogo.show()
                } else {
                    binding.ddgLogo.gone()
                }
            }

            if (viewState.sections.isEmpty()) {
                binding.newTabContentShimmer.gone()
                binding.newTabSectionsContent.gone()
            } else {
                binding.newTabSectionsContent.setOnHierarchyChangeListener(
                    object : OnHierarchyChangeListener {
                        var childsAdded = 0
                        override fun onChildViewAdded(
                            p0: View?,
                            p1: View?,
                        ) {
                            childsAdded++
                            logcat { "New Tab Render: child added $childsAdded" }
                            if (childsAdded == viewState.sections.size) {
                                binding.newTabContentShimmer.gone()
                                binding.newTabSectionsContent.show()
                            }
                        }

                        override fun onChildViewRemoved(
                            p0: View?,
                            p1: View?,
                        ) {
                        }
                    },
                )

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
                binding.newTabSectionsContent.show()
            }
        }
    }

    private fun setClickListeners() {
        binding.newTabEditScroll.setOnClickListener {
            viewModel.onCustomisePageClicked()
            globalActivityStarter.start(context, NewTabSettingsScreenNoParams)
        }

        binding.newTabEditAnchor.setOnClickListener {
            viewModel.onCustomisePageClicked()
            globalActivityStarter.start(context, NewTabSettingsScreenNoParams)
        }
    }

    private fun setAnimationListeners() {
        binding.newTabContentScroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            binding.root.requestFocus()
            binding.root.hideKeyboard()
        }

        binding.newTabContentScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            // If the change in screen
            if (keypadHeight > screenHeight * 0.15) {
                binding.newTabEditAnchor.hide()
            } else {
                // If the scrollView can scroll, hide the button
                if (binding.newTabContentScroll.canScrollVertically(1) || binding.newTabContentScroll.canScrollVertically(-1)) {
                    binding.newTabEditAnchor.hide()
                    binding.newTabEditScroll.show()
                } else {
                    binding.newTabEditAnchor.show()
                    binding.newTabEditScroll.hide()
                }
            }
        }
    }
}
