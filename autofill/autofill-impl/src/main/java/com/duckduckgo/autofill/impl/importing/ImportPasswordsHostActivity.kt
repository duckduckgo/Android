/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.Unknown
import com.duckduckgo.autofill.api.AutofillScreens.AutofillImportPasswordsScreen
import com.duckduckgo.autofill.impl.importing.ImportPasswordsHostViewModel.Command
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.credentialexchange.api.CredentialExchangeLauncher
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportPasswordsHostScreen(val source: AutofillImportLaunchSource) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ImportPasswordsHostScreen::class)
class ImportPasswordsHostActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var credentialExchangeLauncher: CredentialExchangeLauncher

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: ImportPasswordsHostViewModel by bindViewModel()

    private val launchSource: AutofillImportLaunchSource
        get() = intent.getActivityParams(ImportPasswordsHostScreen::class.java)?.source ?: Unknown

    private val webFlow = registerForActivityResult(StartActivityForResult()) { result ->
        val details = result.data?.let {
            IntentCompat.getParcelableExtra(it, ImportGooglePasswordResult.RESULT_KEY_DETAILS, ImportGooglePasswordResult::class.java)
        }
        viewModel.onWebFlowFinished(result.resultCode, details)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.onCreated(launchSource, isRestored = savedInstanceState != null)
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.StartCredentialExchange -> startCredentialExchange()
            Command.StartWebFlow -> globalActivityStarter.startForResult(this, AutofillImportPasswordsScreen(launchSource), webFlow)
            is Command.Finish -> finishWithResult(command.result)
        }
    }

    private fun finishWithResult(result: ImportPasswordsHostResult) {
        setResult(RESULT_OK, Intent().putExtra(ImportPasswordsHostResult.RESULT_KEY, result))
        finish()
    }

    private fun startCredentialExchange() {
        lifecycleScope.launch {
            val result = credentialExchangeLauncher.launchImportFlow()
            viewModel.onCredentialExchangeResult(result, launchSource)
        }
    }
}
