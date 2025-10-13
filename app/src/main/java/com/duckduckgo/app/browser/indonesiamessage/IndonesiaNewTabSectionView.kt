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

package com.duckduckgo.app.browser.indonesiamessage

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewIndonesiaNewTabSectionBinding
import com.duckduckgo.app.browser.indonesiamessage.IndonesiaNewTabSectionViewModel.ViewState
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class IndonesiaNewTabSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewIndonesiaNewTabSectionBinding by viewBinding()

    private val viewModel: IndonesiaNewTabSectionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[IndonesiaNewTabSectionViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        if (viewState.showMessage) {
            showMessage()
        } else {
            binding.newTabIndonesiaMessage.gone()
        }
    }

    private fun showMessage() {
        with(binding.newTabIndonesiaMessage) {
            setMessage(
                Message(
                    topIllustration = com.duckduckgo.mobile.android.R.drawable.ic_announce,
                    title = context.getString(R.string.newTabPageIndonesiaMessageHeading),
                    subtitle = context.getString(R.string.newTabPageIndonesiaMessageBody),
                    action = context.getString(R.string.newTabPageIndonesiaMessageCta),
                ),
            )
            onPrimaryActionClicked {
                gone()
                viewModel.onMessageDismissed()
            }
            onCloseButtonClicked {
                gone()
                viewModel.onMessageDismissed()
            }
            show()
        }
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_INDONESIA_MESSAGE,
)
class IndonesiaNewTabSectionPlugin @Inject constructor() : NewTabPageSectionPlugin {

    override val name = NewTabPageSection.INDONESIA_MESSAGE.name

    override fun getView(context: Context): View {
        return IndonesiaNewTabSectionView(context)
    }

    override suspend fun isUserEnabled(): Boolean {
        return true
    }
}
