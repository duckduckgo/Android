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

package com.duckduckgo.autofill.impl.service

import android.app.assist.AssistStructure
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ActivityCustomAutofillProviderBinding
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.AutofillLogin
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.ContinueWithoutAuthentication
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.ForceFinish
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.RequestAuthentication
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AutofillProviderFillSuggestionActivity : DuckDuckGoActivity() {

    val binding: ActivityCustomAutofillProviderBinding by viewBinding()
    private val viewModel: AutofillProviderChooseViewModel by bindViewModel()

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var autofillServiceActivityHandler: AutofillServiceActivityHandler

    private var assistStructure: AssistStructure? = null

    private val credentialId: Long?
        get() = intent.getLongExtra(FILL_REQUEST_AUTOFILL_CREDENTIAL_ID_EXTRAS, -1L).takeIf { it != -1L }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logcat(INFO) { "DDGAutofillService onCreate!" }

        assistStructure = IntentCompat.getParcelableExtra(intent, AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)

        if (assistStructure == null || credentialId == null) {
            finish()
            return
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is RequestAuthentication -> {
                logcat(INFO) { "DDGAutofillService auth IS REQUIRED!" }
                deviceAuthenticator.authenticate(this) {
                    when (it) {
                        Success -> {
                            viewModel.onUserAuthenticatedSuccessfully()
                        }

                        UserCancelled -> {
                            finish()
                        }

                        is Error -> {
                            finish()
                        }
                    }
                }
            }

            ContinueWithoutAuthentication -> {
                logcat(INFO) { "DDGAutofillService ContinueWithoutAuthentication credentialId: $credentialId" }
                credentialId?.let { nonNullId ->
                    viewModel.continueAfterAuthentication(nonNullId)
                } ?: run {
                    finish()
                }
            }

            is AutofillLogin -> {
                autofillLogin(command.credentials)
            }

            ForceFinish -> finish()
        }
    }

    fun autofillLogin(credential: LoginCredentials) {
        val structure = assistStructure ?: return
        autofillServiceActivityHandler.onFillRequest(this, credential, structure)
    }

    companion object {
        const val FILL_REQUEST_AUTOFILL_ID_EXTRAS = "AUTOFILL_ID"
        const val FILL_REQUEST_AUTOFILL_CREDENTIAL_ID_EXTRAS = "USER_CREDENTIAL_ID"
    }
}
