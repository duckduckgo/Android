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

import com.duckduckgo.app.global.AppUrl
import io.reactivex.Completable
import okhttp3.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PixelService {

    // We don't have tablet specific UI at all on Android yet, so form factor is irrelevant, thus default to phone
    @GET("/t/{pixelName}?p=android&f=phone")
    fun fire(@Path("pixelName") pixelName: String, @Query(AppUrl.ParamKey.ATB) atb: String) : Completable

}