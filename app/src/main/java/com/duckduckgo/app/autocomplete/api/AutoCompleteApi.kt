/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.autocomplete.api

import com.duckduckgo.app.global.UriString
import io.reactivex.Observable
import javax.inject.Inject


open class AutoCompleteApi @Inject constructor(private val autoCompleteService: AutoCompleteService) {

    fun autoComplete(query: String): Observable<AutoCompleteApi.AutoCompleteResult> {

        if (query.isBlank()) {
            return Observable.just(AutoCompleteResult(query, emptyList()))
        }

        return autoCompleteService.autoComplete(query)
            .flatMapIterable { it }
            .map { AutoCompleteSuggestion(it.phrase, UriString.isWebUrl(it.phrase)) }
            .toList()
            .onErrorReturn { emptyList() }
            .map { AutoCompleteResult(query = query, suggestions = it) }
            .toObservable()
    }

    data class AutoCompleteResult(
        val query: String,
        val suggestions: List<AutoCompleteSuggestion>
    )

    data class AutoCompleteSuggestion(val phrase: String, val isUrl: Boolean)

}