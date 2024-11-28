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

import android.R
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.SavedDatasetsInfoCallback
import android.view.View
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(
    scope = VpnScope::class, // we might need to have our own scope to avoid creating the whole app graph
)
class DDGAutofillService : AutofillService() {

    @Inject
    @AppCoroutineScope
    lateinit var coroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var autofillStore: AutofillStore

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        Timber.i("DDGAutofillService onFillRequest")
        coroutineScope.launch(dispatcherProvider.io()) {
            Timber.i("DDGAutofillService structure: ${request.fillContexts}")
            val structure = request.fillContexts.last().structure ?: return@launch
            Timber.i("DDGAutofillService structure: $structure")

            // Extract package name
            val packageName = structure.activityComponent?.packageName.orEmpty()
            Timber.i("DDGAutofillService packageName: $packageName")

            val fields = findFields(packageName, structure)

            if (fields.isNotEmpty()) {
                val dataset = createDataset(fields)

                if (dataset == null) {
                    callback.onFailure("No dataset found.")
                    return@launch
                }

                val response = FillResponse.Builder()
                    .addDataset(dataset)
                    .build()
                callback.onSuccess(response)
            } else {
                callback.onFailure("No suitable fields found.")
            }
        }
    }

    private suspend fun createDataset(fieldsRoot: Map<String, MutableMap<String, ViewNode>>): Dataset? {
        Timber.i("DDGAutofillService fieldsRoot keys: ${fieldsRoot.keys}")
        val firstNonEmptyOrigin = fieldsRoot.keys.first { it.isNotEmpty() }
        val fields = fieldsRoot.values.lastOrNull()?.let { fields ->
            Timber.i("DDGAutofillService fields: $fields")
            fields
        } ?: return null

        val credential = autofillStore.getCredentials(firstNonEmptyOrigin).firstOrNull() ?: return null

        Timber.i("DDGAutofillService we have credentials ${credential.username} to use in -> $fields")
        val datasetBuilder = Dataset.Builder()
        fields["username"]?.let { usernameNode ->
            val username = credential.username // Retrieve from your secure storage
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            presentation.setTextViewText(android.R.id.text1, username)
            datasetBuilder.setValue(
                usernameNode.autofillId!!,
                AutofillValue.forText(username),
                presentation
            )
        }
        fields["password"]?.let { passwordNode ->
            val password = credential.password // Retrieve from your secure storage
            val presentation = RemoteViews(packageName, R.layout.simple_list_item_1)
            presentation.setTextViewText(android.R.id.text1, "Password for ${credential.username}")
            datasetBuilder.setValue(
                passwordNode.autofillId!!,
                AutofillValue.forText(password),
                presentation
            )
        }
        return datasetBuilder.build()
    }

    private fun findFields(
        packageName: String,
        structure: AssistStructure
    ): Map<String, MutableMap<String, ViewNode>>{
        val fields = mutableMapOf<String, MutableMap<String, ViewNode>>()
        val windowNodeCount = structure.windowNodeCount
        Timber.i("DDGAutofillService windowNodeCount: $windowNodeCount")
        for (i in 0 until windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootViewNode = windowNode.rootViewNode
            traverseNode(rootViewNode, packageName, fields)
        }
        return fields
    }

    private fun traverseNode(node: ViewNode, packageName: String, fields: MutableMap<String, MutableMap<String, ViewNode>>) {
        Timber.i("DDGAutofillService node web: ${node.webDomain}")
        val domain = node.webDomain ?: packageName

        node.autofillHints?.let { hints ->
            Timber.i("DDGAutofillService hints for $node: $hints")
            for (hint in hints) {
                Timber.i("DDGAutofillService hint: $hint")
                when (hint) {
                    View.AUTOFILL_HINT_USERNAME -> {
                        Timber.i("DDGAutofillService hint is username for $domain")
                        fields[domain]?.let {
                            it["username"] = node
                        } ?: run {
                            fields[domain] = mutableMapOf("username" to node)
                        }
                    }
                    View.AUTOFILL_HINT_PASSWORD -> {
                        Timber.i("DDGAutofillService hint is password for $domain")
                        fields[domain]?.let {
                            it["password"] = node
                        } ?: run {
                            fields[domain] = mutableMapOf("password" to node)
                        }
                    }
                    View.AUTOFILL_HINT_EMAIL_ADDRESS -> {
                        Timber.i("DDGAutofillService hint is EMAIL for $domain")
                        fields[domain]?.let {
                            it["username"] = node
                        } ?: run {
                            fields[domain] = mutableMapOf("username" to node)
                        }
                    }
                    else -> {
                        Timber.i("DDGAutofillService hint is unknown: $hint")
                    }
                }
            }
        }
        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), packageName, fields)
        }
    }

    override fun onSaveRequest(
        request: SaveRequest, callback: SaveCallback
    ) {
        Timber.i("DDGAutofillService onSaveRequest")
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("DDGAutofillService created")
        AndroidInjection.inject(this)
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
