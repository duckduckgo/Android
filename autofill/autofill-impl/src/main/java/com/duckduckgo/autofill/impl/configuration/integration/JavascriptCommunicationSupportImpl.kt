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

package com.duckduckgo.autofill.impl.configuration.integration

import androidx.webkit.WebViewFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface JavascriptCommunicationSupport {
    fun supportsModernIntegration(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class JavascriptCommunicationSupportImpl @Inject constructor() : JavascriptCommunicationSupport {

    private val isModernSupportAvailable by lazy {
        autofillRequiredFeatures.forEach { requiredFeature ->
            if (!WebViewFeature.isFeatureSupported(requiredFeature)) {
                Timber.i("Modern integration is not supported because feature %s is not supported", requiredFeature)
                return@lazy false
            }
        }

        return@lazy true
    }

    override fun supportsModernIntegration(): Boolean = isModernSupportAvailable

    companion object {

        /**
         * We need all of these to be supported in order to use autofill
         */
        private val autofillRequiredFeatures = listOf(
            WebViewFeature.DOCUMENT_START_SCRIPT,
            WebViewFeature.WEB_MESSAGE_LISTENER,
            WebViewFeature.POST_WEB_MESSAGE,
        )
    }
}
