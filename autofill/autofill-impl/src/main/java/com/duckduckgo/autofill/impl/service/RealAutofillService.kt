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

package com.duckduckgo.autofill.impl.service

import android.os.Build.VERSION_CODES
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.SavedDatasetsInfoCallback
import androidx.annotation.RequiresApi
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(
    scope = VpnScope::class, // we might need to have our own scope to avoid creating the whole app graph
)
class RealAutofillService : AutofillService() {

    @Inject
    @AppCoroutineScope
    lateinit var coroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var autofillParser: AutofillParser

    @Inject lateinit var autofillProviderSuggestions: AutofillProviderSuggestions

    private val autofillJob = ConflatedJob()

    override fun onCreate() {
        super.onCreate()
        Timber.i("DDGAutofillService created")
        AndroidInjection.inject(this)
    }

    @RequiresApi(VERSION_CODES.R)
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        Timber.i("DDGAutofillService onFillRequest: $request")
        cancellationSignal.setOnCancelListener { autofillJob.cancel() }

        autofillJob += coroutineScope.launch(dispatcherProvider.io()) {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return@launch
            }
            // TODO: return if it's ddg app?

            val parsedRootNodes = autofillParser.parseStructure(structure)
            val autofillParsedRequest: AutofillParsedRequest? = findFillableNode(parsedRootNodes)
            if (autofillParsedRequest == null) {
                callback.onSuccess(null)
                return@launch
            }
            val logSpecs = request.inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull()
            Timber.i("DDGAutofillService onFillRequest logSpecs: $logSpecs")
            Timber.i("DDGAutofillService onFillRequest maxSuggestionCount: ${request.inlineSuggestionsRequest?.maxSuggestionCount}")

            // prepare response
            val response = autofillProviderSuggestions.buildSuggestionsResponse(
                context = this@RealAutofillService,
                autofillRequest = autofillParsedRequest,
                request = request,
            )

            callback.onSuccess(response)
        }
    }

    private fun findFillableNode(rootNodes: List<AutofillRootNode>): AutofillParsedRequest? {
        return rootNodes.firstNotNullOfOrNull { rootNode ->
            val focusedDetectedField = rootNode.parsedAutofillFields
                .firstOrNull { field ->
                    field.originalNode.isFocused && field.type != UNKNOWN
                }
            if (focusedDetectedField != null) {
                return@firstNotNullOfOrNull AutofillParsedRequest(rootNode, focusedDetectedField)
            }

            val firstDetectedField = rootNode.parsedAutofillFields.firstOrNull { field -> field.type != UNKNOWN }
            if (firstDetectedField != null) {
                return@firstNotNullOfOrNull AutofillParsedRequest(rootNode, firstDetectedField)
            }
            return@firstNotNullOfOrNull null
        }
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        TODO("Not yet implemented")
    }

    override fun onConnected() {
        super.onConnected()
        Timber.i("DDGAutofillService onConnected")
    }

    override fun onSavedDatasetsInfoRequest(callback: SavedDatasetsInfoCallback) {
        super.onSavedDatasetsInfoRequest(callback)
        Timber.i("DDGAutofillService onSavedDatasetsInfoRequest")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Timber.i("DDGAutofillService onDisconnected")
    }
}

class AutofillParsedRequest(
    val rootNode: AutofillRootNode,
    val fillRequestNode: ParsedAutofillField,
)
