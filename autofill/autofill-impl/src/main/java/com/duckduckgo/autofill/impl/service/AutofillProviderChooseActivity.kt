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
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
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
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class AutofillProviderChooseActivity : DuckDuckGoActivity() {

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

    private val urlRequest: String
        get() = intent.getStringExtra(FILL_REQUEST_URL_EXTRAS) ?: ""

    private val packageRequest: String
        get() = intent.getStringExtra(FILL_REQUEST_PACKAGE_ID_EXTRAS) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("DDGAutofillService onCreate!")

        assistStructure = IntentCompat.getParcelableExtra(intent, AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)

        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setTitle(R.string.autofill_service_select_password_activity)
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
                Timber.i("DDGAutofillService auth IS REQUIRED!")
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
                Timber.i("DDGAutofillService ContinueWithoutAuthentication credentialId: $credentialId")
                credentialId?.let { nonNullId ->
                    viewModel.continueAfterAuthentication(nonNullId)
                } ?: run {
                    showListMode()
                }
            }

            is AutofillLogin -> {
                autofillLogin(command.credentials)
            }

            ForceFinish -> finish()
        }
    }

    private fun showListMode() {
        supportFragmentManager.commitNow {
            val fragment = AutofillSimpleCredentialsListFragment.instance(urlRequest, packageRequest)
            replace(R.id.fragment_container_view, fragment, TAG_CREDENTIALS_LIST)
        }
    }

    fun showSearchBar() {
        with(binding) {
            toolbar.gone()
            searchBar.handle(SearchBar.Event.ShowSearchBar)
            searchBar.showKeyboard()
        }
    }

    fun hideSearchBar() {
        with(binding) {
            toolbar.show()
            searchBar.handle(SearchBar.Event.DismissSearchBar)
            searchBar.hideKeyboard()
        }
    }

    private fun isSearchBarVisible(): Boolean = binding.searchBar.isVisible

    override fun onBackPressed() {
        if (isSearchBarVisible()) {
            hideSearchBar()
        } else {
            super.onBackPressed()
        }
    }

    fun autofillLogin(credential: LoginCredentials) {
        val structure = assistStructure ?: return
        autofillServiceActivityHandler.onFillRequest(this, credential, structure)
    }

    companion object {
        const val TAG_CREDENTIALS_LIST = "tag_fragment_credentials_list"
        const val FILL_REQUEST_URL_EXTRAS = "FILL_REQUEST_URL"
        const val FILL_REQUEST_PACKAGE_ID_EXTRAS = "FILL_REQUEST_PACKAGE_ID"
        const val FILL_REQUEST_AUTOFILL_ID_EXTRAS = "AUTOFILL_ID"
        const val FILL_REQUEST_AUTOFILL_CREDENTIAL_ID_EXTRAS = "USER_CREDENTIAL_ID"
    }
}
