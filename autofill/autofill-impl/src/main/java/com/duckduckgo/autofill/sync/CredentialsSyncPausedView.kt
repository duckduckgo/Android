/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.Sync
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.impl.databinding.ViewCredentialsSyncPausedWarningBinding
import com.duckduckgo.autofill.sync.CredentialsSyncPausedViewModel.Command
import com.duckduckgo.autofill.sync.CredentialsSyncPausedViewModel.Command.NavigateToCredentials
import com.duckduckgo.autofill.sync.CredentialsSyncPausedViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class CredentialsSyncPausedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private var job: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    private val binding: ViewCredentialsSyncPausedWarningBinding by viewBinding()

    private val viewModel: CredentialsSyncPausedViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[CredentialsSyncPausedViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState()
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    private fun processCommands(command: Command) {
        when (command) {
            NavigateToCredentials -> navigateToCredentials()
        }
    }

    private fun render(viewState: ViewState) {
        if (viewState.message != null) {
            this.isVisible = true
            binding.credentialsSyncPausedWarning.setClickableLink(
                WARNING_ACTION_ANNOTATION,
                context.getText(viewState.message),
                onClick = {
                    viewModel.onWarningActionClicked()
                },
            )
        } else {
            this.isVisible = false
        }
    }

    private fun navigateToCredentials() {
        globalActivityStarter.start(this.context, AutofillPasswordsManagementScreen(source = Sync))
    }

    companion object {
        const val WARNING_ACTION_ANNOTATION = "manage_logins"
    }
}
