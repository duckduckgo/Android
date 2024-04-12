/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.jsbridge

import android.annotation.SuppressLint
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface AutofillMessagePoster {
    fun postMessage(message: String, requestId: String)
}

@SuppressLint("RequiresFeature")
@SingleInstanceIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class)
class AutofillWebViewMessagePoster @Inject constructor(
    private val webMessageListeners: PluginPoint<AutofillWebMessageListener>,
) : AutofillMessagePoster {

    override fun postMessage(message: String, requestId: String) {
        webMessageListeners.getPlugins().firstOrNull { it.onResponse(message, requestId) } ?: {
            Timber.w("No listener found for requestId: %s", requestId)
        }
    }
}
