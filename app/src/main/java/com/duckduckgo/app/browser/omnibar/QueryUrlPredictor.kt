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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.urlpredictor.Decision
import com.duckduckgo.urlpredictor.UrlPredictor
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface QueryUrlPredictor {

    /**
     * Classifies the input string as a navigable URL or a search query.
     */
    fun classify(input: String): Decision
}

/**
 * Wrapper around the native implementation of [UrlPredictor] to allow unit testing.
 */
@ContributesBinding(scope = AppScope::class)
class QueryUrlPredictorImpl @Inject constructor() : QueryUrlPredictor {
    override fun classify(input: String): Decision = UrlPredictor.classify(input)
}
