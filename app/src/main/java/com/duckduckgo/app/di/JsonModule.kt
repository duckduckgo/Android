/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeDomainFromStringAdapter
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomain
import com.duckduckgo.app.privacymonitor.api.TermsOfServiceListAdapter
import com.duckduckgo.app.trackerdetection.api.DisconnectListJsonAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class JsonModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder()
            .add(DisconnectListJsonAdapter())
            .add(TermsOfServiceListAdapter())
            .add(HttpsUpgradeDomainFromStringAdapter())
            .build()

    @Provides
    fun httpsUpgradeDomainAdapter(moshi: Moshi): JsonAdapter<List<HttpsUpgradeDomain>> {
        val type = Types.newParameterizedType(List::class.java, HttpsUpgradeDomain::class.java)
        return moshi.adapter(type)
    }

}