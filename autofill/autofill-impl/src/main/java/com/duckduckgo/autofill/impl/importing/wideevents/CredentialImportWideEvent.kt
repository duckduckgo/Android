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

package com.duckduckgo.autofill.impl.importing.wideevents

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.WebViewCrash
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * One event per password import attempt, from the import tap to the result, across both the credential exchange and the Google web flow.
 * A credential exchange failure that falls back to the web flow stays in the same event.
 *
 * Calls return at once and are recorded in call order, so the UI reporting them can be destroyed straight after.
 */
interface CredentialImportWideEvent {
    fun onImportStarted(source: AutofillImportLaunchSource, usesCredentialExchange: Boolean)

    /** Call after the credentials are handed to [CredentialImporter]. The event finishes when that import finishes. */
    fun onCredentialExchangeSucceeded(exporterPackageName: String?)
    fun onCredentialExchangeFellBackToWebFlow(reason: CredentialExchangeFailure, exporterPackageName: String?)
    fun onCredentialExchangeCancelled()
    fun onCredentialExchangeFailed(reason: CredentialExchangeFailure, exporterPackageName: String?)

    /** Call after the credentials are handed to [CredentialImporter]. The event finishes when that import finishes. */
    fun onWebFlowSucceeded()
    fun onWebFlowCancelled()
    fun onWebFlowFailed(reason: UserCannotImportReason)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealCredentialImportWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val engagementBucketing: AutofillEngagementBucketing,
    private val credentialImporter: CredentialImporter,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : CredentialImportWideEvent {

    // a single consumer keeps the calls in order
    private val pending = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var cachedFlowId: Long? = null
    private var importCompletionJob: Job? = null

    private val consumer = appCoroutineScope.launch(dispatchers.io(), start = CoroutineStart.LAZY) {
        for (action in pending) {
            try {
                action()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logcat(WARN) { "Credential import wide event failed: ${e.message}" }
            }
        }
    }

    override fun onImportStarted(source: AutofillImportLaunchSource, usesCredentialExchange: Boolean) = enqueue {
        importCompletionJob?.cancel()

        wideEventClient.getFlowIds(FLOW_NAME).getOrNull()?.forEach { staleId ->
            wideEventClient.flowFinish(wideEventId = staleId, status = FlowStatus.Unknown)
        }

        val flow = if (usesCredentialExchange) FLOW_CREDENTIAL_EXCHANGE else FLOW_WEB_FLOW
        cachedFlowId = wideEventClient.flowStart(
            name = FLOW_NAME,
            flowEntryPoint = source.value,
            metadata = mapOf(
                KEY_INITIAL_FLOW to flow,
                KEY_FALLBACK to false.toString(),
                KEY_LAST_STEP to flow,
            ),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        ).getOrNull()?.also { id ->
            wideEventClient.intervalStart(wideEventId = id, key = KEY_TOTAL_DURATION)
        }
    }

    override fun onCredentialExchangeSucceeded(exporterPackageName: String?) = enqueue {
        val id = currentFlowId() ?: return@enqueue
        wideEventClient.flowStep(
            wideEventId = id,
            stepName = STEP_CREDENTIAL_EXCHANGE,
            success = true,
            metadata = mapOf(
                KEY_EXPORTER to exporterPackageName.toExporter(),
                KEY_LAST_STEP to LAST_STEP_IMPORTING,
            ),
        )
        finishWhenImportCompletes(id)
    }

    override fun onCredentialExchangeFellBackToWebFlow(reason: CredentialExchangeFailure, exporterPackageName: String?) = enqueue {
        val id = currentFlowId() ?: return@enqueue
        wideEventClient.flowStep(
            wideEventId = id,
            stepName = STEP_CREDENTIAL_EXCHANGE,
            success = false,
            metadata = cxfFailureMetadata(reason, exporterPackageName) + mapOf(
                KEY_FALLBACK to true.toString(),
                KEY_LAST_STEP to FLOW_WEB_FLOW,
            ),
        )
    }

    override fun onCredentialExchangeFailed(reason: CredentialExchangeFailure, exporterPackageName: String?) = enqueue {
        val id = currentFlowId() ?: return@enqueue
        wideEventClient.flowStep(
            wideEventId = id,
            stepName = STEP_CREDENTIAL_EXCHANGE,
            success = false,
            metadata = cxfFailureMetadata(reason, exporterPackageName),
        )
        finish(FlowStatus.Failure(FAILURE_CREDENTIAL_EXCHANGE))
    }

    override fun onCredentialExchangeCancelled() = enqueue {
        finish(FlowStatus.Cancelled)
    }

    override fun onWebFlowSucceeded() = enqueue {
        val id = currentFlowId() ?: return@enqueue
        wideEventClient.flowStep(
            wideEventId = id,
            stepName = STEP_WEB_FLOW,
            success = true,
            metadata = mapOf(KEY_LAST_STEP to LAST_STEP_IMPORTING),
        )
        finishWhenImportCompletes(id)
    }

    override fun onWebFlowCancelled() = enqueue {
        finish(FlowStatus.Cancelled)
    }

    override fun onWebFlowFailed(reason: UserCannotImportReason) = enqueue {
        val id = currentFlowId() ?: return@enqueue
        wideEventClient.flowStep(wideEventId = id, stepName = STEP_WEB_FLOW, success = false)
        finish(FlowStatus.Failure(reason.toFailureReason()))
    }

    private fun enqueue(action: suspend () -> Unit) {
        pending.trySend(action)
        consumer.start()
    }

    private fun finishWhenImportCompletes(flowId: Long) {
        importCompletionJob?.cancel()

        importCompletionJob = appCoroutineScope.launch(dispatchers.io()) {
            val result = credentialImporter.getImportStatus().filterIsInstance<Finished>().first()

            enqueue {
                // a new attempt may have started while this import was saving
                if (cachedFlowId != flowId) return@enqueue

                finish(
                    FlowStatus.Success,
                    metadata = mapOf(
                        KEY_SAVED_CREDENTIALS to engagementBucketing.bucketNumberOfCredentials(result.savedCredentials),
                        KEY_SKIPPED_CREDENTIALS to engagementBucketing.bucketNumberOfCredentials(result.numberSkipped),
                    ),
                )
            }
        }
    }

    private suspend fun finish(status: FlowStatus, metadata: Map<String, String> = emptyMap()) {
        val id = currentFlowId() ?: return
        wideEventClient.intervalEnd(wideEventId = id, key = KEY_TOTAL_DURATION)
        wideEventClient.flowFinish(wideEventId = id, status = status, metadata = metadata)
        cachedFlowId = null
    }

    private suspend fun currentFlowId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient.getFlowIds(FLOW_NAME).getOrNull()?.lastOrNull()
        }
        return cachedFlowId
    }

    // the package name is only known when the exporter responded, e.g. with a payload we could not parse
    private fun cxfFailureMetadata(reason: CredentialExchangeFailure, exporterPackageName: String?): Map<String, String> = buildMap {
        put(KEY_CXF_FAILURE_REASON, reason.name.lowercase())
        exporterPackageName?.let { put(KEY_EXPORTER, it.toExporter()) }
    }

    private fun String?.toExporter(): String = KNOWN_EXPORTERS[this] ?: EXPORTER_OTHER

    private fun UserCannotImportReason.toFailureReason(): String = when (this) {
        ErrorParsingCsv -> FAILURE_ERROR_PARSING
        WebViewCrash -> FAILURE_WEBVIEW_CRASH
    }

    private companion object {
        const val FLOW_NAME = "credential-import"

        const val KEY_INITIAL_FLOW = "initial_flow"
        const val KEY_FALLBACK = "fallback"
        const val KEY_CXF_FAILURE_REASON = "cxf_failure_reason"
        const val KEY_EXPORTER = "exporter"
        const val KEY_SAVED_CREDENTIALS = "saved_credentials"
        const val KEY_SKIPPED_CREDENTIALS = "skipped_credentials"
        const val KEY_TOTAL_DURATION = "total_duration_ms_bucketed"
        const val KEY_LAST_STEP = "last_step"

        const val FLOW_CREDENTIAL_EXCHANGE = "credential_exchange"
        const val FLOW_WEB_FLOW = "webflow"

        const val LAST_STEP_IMPORTING = "importing"

        const val STEP_CREDENTIAL_EXCHANGE = "credential_exchange"
        const val STEP_WEB_FLOW = "webflow"

        const val FAILURE_ERROR_PARSING = "error_parsing"
        const val FAILURE_WEBVIEW_CRASH = "webview_crash"
        const val FAILURE_CREDENTIAL_EXCHANGE = "credential_exchange_failed"

        const val EXPORTER_OTHER = "other"
        val KNOWN_EXPORTERS = mapOf(
            "com.google.android.gms" to "google",
            "com.onepassword.android" to "1password",
            "com.x8bit.bitwarden" to "bitwarden",
            "com.dashlane" to "dashlane",
        )
    }
}
