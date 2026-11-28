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

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportHandler
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportOutcome
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import com.duckduckgo.autofill.impl.importing.wideevents.CredentialImportWideEvent
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ImportPasswordsHostViewModel @Inject constructor(
    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter,
    private val credentialExchangeImportHandler: CredentialExchangeImportHandler,
    private val credentialImportWideEvent: CredentialImportWideEvent,
    private val passwordImportExperimentMetrics: PasswordImportExperimentMetrics,
) : ViewModel() {

    private enum class Stage {
        NotStarted,

        /** The exchange runs in the Activity's scope, so it does not survive the Activity being recreated. */
        CredentialExchange,

        /** Choosing the method, saving an exchange result or waiting on the web flow; survives the Activity being recreated. */
        Running,
        Finished,
    }

    sealed interface Command {
        data object StartCredentialExchange : Command
        data object StartWebFlow : Command
        data class Finish(val result: ImportPasswordsHostResult) : Command
    }

    private var stage: Stage = Stage.NotStarted

    private val commands = Channel<Command>(Channel.BUFFERED)

    fun commands(): Flow<Command> = commands.receiveAsFlow()

    /**
     * Call on every creation of the host Activity.
     *
     * @param isRestored whether the Activity is being restored from saved state rather than launched.
     */
    fun onCreated(source: AutofillImportLaunchSource, isRestored: Boolean) {
        when {
            stage == Stage.CredentialExchange -> finish(ImportPasswordsHostResult.UserCancelled)
            stage != Stage.NotStarted -> Unit
            // A fresh ViewModel in a restored Activity means the process died, taking the running import with it.
            isRestored -> finish(ImportPasswordsHostResult.UserCancelled)
            else -> start(source)
        }
    }

    fun onCredentialExchangeResult(result: CredentialExchangeResult, source: AutofillImportLaunchSource) {
        stage = Stage.Running
        viewModelScope.launch {
            when (credentialExchangeImportHandler.handle(result, source)) {
                CredentialExchangeImportOutcome.Imported -> finish(ImportPasswordsHostResult.Success)
                CredentialExchangeImportOutcome.UserCancelled -> {
                    passwordImportExperimentMetrics.fireImportCancelledMetric()
                    finish(ImportPasswordsHostResult.UserCancelled)
                }
                CredentialExchangeImportOutcome.FallBackToWebFlow -> startWebFlow()
                CredentialExchangeImportOutcome.Failed -> {
                    passwordImportExperimentMetrics.fireImportFailedMetric()
                    finish(ImportPasswordsHostResult.PermanentError)
                }
            }
        }
    }

    fun onWebFlowFinished(resultCode: Int, result: ImportGooglePasswordResult?) {
        if (resultCode != Activity.RESULT_OK) {
            finish(ImportPasswordsHostResult.UserCancelled)
            return
        }
        when (result) {
            is ImportGooglePasswordResult.Success -> {
                credentialImportWideEvent.onWebFlowSucceeded()
                finish(ImportPasswordsHostResult.Success)
            }
            is ImportGooglePasswordResult.UserCancelled -> {
                credentialImportWideEvent.onWebFlowCancelled()
                finish(ImportPasswordsHostResult.UserCancelled)
            }
            is ImportGooglePasswordResult.Error -> {
                credentialImportWideEvent.onWebFlowFailed(result.reason)
                when (result.reason) {
                    is ErrorParsingCsv -> finish(ImportPasswordsHostResult.PermanentError)
                    is WebViewCrash -> finish(ImportPasswordsHostResult.TransientError)
                }
            }
            null -> finish(ImportPasswordsHostResult.TransientError)
        }
    }

    private fun start(source: AutofillImportLaunchSource) {
        stage = Stage.Running
        viewModelScope.launch {
            val usesCredentialExchange = credentialExchangePasswordImporter.isSupported()
            credentialImportWideEvent.onImportStarted(source, usesCredentialExchange)
            if (usesCredentialExchange) {
                // The web flow fires its own experiment metrics, so only the exchange path fires them here.
                passwordImportExperimentMetrics.fireImportStartedMetric()
                stage = Stage.CredentialExchange
                commands.send(Command.StartCredentialExchange)
            } else {
                startWebFlow()
            }
        }
    }

    private suspend fun startWebFlow() {
        stage = Stage.Running
        commands.send(Command.StartWebFlow)
    }

    private fun finish(result: ImportPasswordsHostResult) {
        stage = Stage.Finished
        viewModelScope.launch { commands.send(Command.Finish(result)) }
    }
}
