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

package com.duckduckgo.autofill.sync

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.Sync
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ViewCredentialsInvalidItemsWarningBinding
import com.duckduckgo.autofill.sync.CredentialsInvalidItemsViewModel.Command
import com.duckduckgo.autofill.sync.CredentialsInvalidItemsViewModel.Command.NavigateToCredentials
import com.duckduckgo.autofill.sync.CredentialsInvalidItemsViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class CredentialsInvalidItemsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private var coroutineScope: CoroutineScope? = null

    private var job: ConflatedJob = ConflatedJob()

    private val binding: ViewCredentialsInvalidItemsWarningBinding by viewBinding()

    private val viewModel: CredentialsInvalidItemsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[CredentialsInvalidItemsViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main())

        viewModel.viewState()
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        job.cancel()
        coroutineScope = null
    }

    private fun processCommands(command: Command) {
        when (command) {
            NavigateToCredentials -> navigateToCredentials()
        }
    }

    private fun render(viewState: ViewState) {
        this.isVisible = viewState.warningVisible

        val spannable = SpannableStringBuilder(
            context.resources.getQuantityString(
                R.plurals.syncCredentialInvalidItemsWarning,
                viewState.invalidItemsSize,
                viewState.hint,
                viewState.invalidItemsSize - 1,
            ),
        ).append(context.getText(R.string.syncCredentialInvalidItemsWarningLink))

        binding.credentialsInvalidItemsWarning.setClickableLink(
            "manage_passwords",
            spannable,
            onClick = {
                viewModel.onWarningActionClicked()
            },
        )
    }

    private fun navigateToCredentials() {
        globalActivityStarter.start(this.context, AutofillPasswordsManagementScreen(source = Sync))
    }
}
