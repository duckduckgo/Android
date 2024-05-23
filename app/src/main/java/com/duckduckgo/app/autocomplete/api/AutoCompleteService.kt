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

import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson
import io.reactivex.Observable
import java.util.*
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

@ContributesNonCachingServiceApi(AppScope::class)
interface AutoCompleteService {
    @GET
    fun autoCompleteWithSearx(
        @Url url: String,
        @Query("q") query: String,
    ): Observable<SearxAutoCompleteResult>


    @GET("${AppUrl.Url.API}/ac/")
    fun autoCompleteWithDDG(
        @Query("q") query: String,
        @Query("kl") languageCode: String = Locale.getDefault().language,
        @Query("is_nav") nav: String = "1",
    ): Observable<List<AutoCompleteServiceRawResult>>
}

data class AutoCompleteServiceRawResult(
    val phrase: String,
    val isNav: Boolean?,
)

@JsonClass(generateAdapter = true)
data class SearxAutoCompleteResult(
    val query: String,
    val suggestions: List<String>
)

class SearxAutoCompleteResultJsonAdapter {
    @FromJson
    fun fromJson(jsonReader: JsonReader): SearxAutoCompleteResult {
        var query = ""
        val results = mutableListOf<String>()

        jsonReader.beginArray()
        if (jsonReader.hasNext()) {
            query = jsonReader.nextString()
        }
        if (jsonReader.hasNext()) {
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                results.add(jsonReader.nextString())
            }
            jsonReader.endArray()
        }
        jsonReader.endArray()

        return SearxAutoCompleteResult(query, results)
    }

    @ToJson
    fun toJson(autoCompleteResult: SearxAutoCompleteResult): String {
        TODO("Can I delete it?")
    }
}
