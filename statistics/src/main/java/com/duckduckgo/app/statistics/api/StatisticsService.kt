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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.app.global.AppUrl.ParamKey
import com.duckduckgo.app.statistics.BuildConfig
import com.duckduckgo.app.statistics.model.Atb
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface StatisticsService {

    @GET("/exti/")
    fun exti(
        @Query(ParamKey.ATB) atb: String,
        @Query(ParamKey.DEV_MODE) devMode: Int? = if (BuildConfig.DEBUG) 1 else null
    ): Observable<ResponseBody>

    @GET("/atb.js")
    fun atb(
        @Query(ParamKey.DEV_MODE) devMode: Int? = if (BuildConfig.DEBUG) 1 else null
    ): Observable<Atb>

    @GET("/atb.js")
    fun updateSearchAtb(
        @Query(ParamKey.ATB) atb: String,
        @Query(ParamKey.RETENTION_ATB) retentionAtb: String,
        @Query(ParamKey.DEV_MODE) devMode: Int? = if (BuildConfig.DEBUG) 1 else null
    ): Observable<Atb>

    @GET("/atb.js?at=app_use")
    fun updateAppAtb(
        @Query(ParamKey.ATB) atb: String,
        @Query(ParamKey.RETENTION_ATB) retentionAtb: String,
        @Query(ParamKey.DEV_MODE) devMode: Int? = if (BuildConfig.DEBUG) 1 else null
    ): Observable<Atb>
}
