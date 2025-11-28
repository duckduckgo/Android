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

import com.duckduckgo.app.di.AppCoroutineScope
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

interface QueryUrlPredictor {

    /**
     * Indicates whether the UrlPredictor native library is ready to be used.
     *
     * This method is a convenience wrapper around {@link UrlPredictor#isInitialized()},
     * exposing the initialization state at the application layer.
     *
     * Initialization is triggered asynchronously in this class' constructor, via:
     *
     * <pre>
     * coroutineScope.launch(dispatcherProvider.io()) {
     *     UrlPredictor.init()
     * }
     * </pre>
     *
     * Until initialization completes, calls to {@link #classify(String)} will fail
     * if invoked directly on the underlying UrlPredictor instance; therefore,
     * callers may use {@code isReady()} to determine whether classification is safe.
     *
     * This method does not block and is safe to call from any thread.
     *
     * @return {@code true} if the UrlPredictor native library has been loaded and
     *         the predictor instance is fully initialized; {@code false} otherwise.
     *
     * @see UrlPredictor#isInitialized()
     * @see UrlPredictor#init()
     */
    fun isReady(): Boolean

    /**
     * Classifies the input string as a navigable URL or a search query.
     * You should be calling [isReady] before calling this method to make sure the library is properly initialized
     */
    fun classify(input: String): Decision
}

/**
 * Wrapper around the native implementation of [UrlPredictor] to allow unit testing.
 */
@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class QueryUrlPredictorImpl @Inject constructor(
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
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
}
