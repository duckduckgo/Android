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

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.SavedDatasetsInfoCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(scope = ServiceScope::class)
class RealAutofillService : AutofillService() {

    @Inject
    @AppCoroutineScope
    lateinit var coroutineScope: CoroutineScope

    @Inject lateinit var autofillServiceFeature: AutofillServiceFeature

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var autofillParser: AutofillParser

    @Inject lateinit var autofillProviderSuggestions: AutofillProviderSuggestions

    private val autofillJob = ConflatedJob()

    override fun onCreate() {
        super.onCreate()
        Timber.v("DDGAutofillService created")
        AndroidInjection.inject(this)
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        Timber.v("DDGAutofillService onFillRequest: $request")
        cancellationSignal.setOnCancelListener { autofillJob.cancel() }

        autofillJob += coroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                if (autofillServiceFeature.self().isEnabled().not()) {
                    callback.onSuccess(null)
                    return@launch
                }
                val structure = request.fillContexts.lastOrNull()?.structure
                if (structure == null) {
                    callback.onSuccess(null)
                    return@launch
                }
                val parsedRootNodes = autofillParser.parseStructure(structure)
                val nodeToAutofill = parsedRootNodes.findBestFillableNode()
                if (nodeToAutofill == null || shouldSkipAutofillSuggestions(nodeToAutofill)) {
                    Timber.v("DDGAutofillService onFillRequest: no autofill suggestions")
                    callback.onSuccess(null)
                    return@launch
                }

                // prepare response
                val response = autofillProviderSuggestions.buildSuggestionsResponse(
                    context = this@RealAutofillService,
                    nodeToAutofill = nodeToAutofill,
                    request = request,
                )

                callback.onSuccess(response)
            }.onFailure {
                // TODO: to include a crash pixel here
                callback.onSuccess(null)
            }
        }
    }

    private fun shouldSkipAutofillSuggestions(nodeToAutofill: AutofillRootNode): Boolean {
        if (nodeToAutofill.packageId.isNullOrBlank() && nodeToAutofill.website.isNullOrBlank()) {
            return true
        }

        if (nodeToAutofill.packageId.equals("android", ignoreCase = true)) return true

        if (autofillServiceFeature.canAutofillInsideDDG().isEnabled().not() && nodeToAutofill.packageId in DDG_PACKAGE_IDS) {
            return true
        }

        if (nodeToAutofill.packageId in BROWSERS_PACKAGE_IDS && nodeToAutofill.website.isNullOrBlank()) {
            return true // if a browser we require a website
        }

        return false
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        Timber.v("DDGAutofillService onSaveRequest")
    }

    override fun onConnected() {
        super.onConnected()
        Timber.v("DDGAutofillService onConnected")
    }

    override fun onSavedDatasetsInfoRequest(callback: SavedDatasetsInfoCallback) {
        super.onSavedDatasetsInfoRequest(callback)
        Timber.v("DDGAutofillService onSavedDatasetsInfoRequest")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Timber.v("DDGAutofillService onDisconnected")
    }

    companion object {
        private val DDG_PACKAGE_IDS = setOf(
            "com.duckduckgo.mobile.android",
            "com.duckduckgo.mobile.android.debug",
        )
        private val BROWSERS_PACKAGE_IDS = DDG_PACKAGE_IDS
    }
}
