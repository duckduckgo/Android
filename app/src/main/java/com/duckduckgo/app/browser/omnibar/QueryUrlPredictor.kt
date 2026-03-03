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

package com.duckduckgo.app.browser.omnibar

import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.urlpredictor.Decision
import com.duckduckgo.urlpredictor.UrlPredictor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Wrapper around the native implementation of [UrlPredictor] to allow unit testing.
 */
@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class QueryUrlPredictorImpl @Inject constructor(
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : QueryUrlPredictor {

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            UrlPredictor.init()
            logcat { "UrlPredictor library initialized" }
        }
    }

    override fun isReady(): Boolean {
        return UrlPredictor.isInitialized()
    }

    override fun classify(input: String): Decision = UrlPredictor.get().classify(input)

    override fun isUrl(query: String): Boolean =
        if (androidBrowserConfigFeature.useUrlPredictor().isEnabled() && isReady()) {
            classify(query) is Decision.Navigate
        } else {
            UriString.isWebUrl(query)
        }
}
