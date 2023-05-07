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

package com.duckduckgo.clicktoload.impl.handlers

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.clicktoload.impl.adapters.JSONObjectAdapter
import com.duckduckgo.contentscopescripts.api.MessageHandlerPlugin
import com.duckduckgo.contentscopescripts.api.ResponseListener
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class UnblockMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(
        messageType: String,
        jsonString: String,
        responseListener: ResponseListener,
    ) {
        if (supportedTypes.contains(messageType)) {
            appCoroutineScope.launch(dispatcherProvider.main()) {
                try {
                    val unblockResponse = Response()
                    responseListener.onResponse(getJsonResponse(unblockResponse))
                } catch (e: Exception) {
                    Timber.d(e.localizedMessage)
                }
            }
        }
    }

    override val supportedTypes = listOf("unblockClickToLoadContent")

    private fun getJsonResponse(response: Response): String {
        val jsonAdapter: JsonAdapter<Response> = moshi.adapter(Response::class.java)
        return jsonAdapter.toJson(response).toString()
    }

    data class Message(
        val type: String,
        val options: String,
    )

    data class Response(
        val type: String = "update",
        val feature: String = "clickToLoad",
        val messageType: String = "response",
        val responseMessageType: String = "unblockClickToLoadContent",
        val response: String = "[]",
    )
}
