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

package com.duckduckgo.app.privacy.store

import android.content.Context
import com.duckduckgo.app.browser.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrevalenceRawStore @Inject constructor(
    moshi: Moshi,
    context: Context
) : PrevalenceStore {

    private var data: Map<String, Double> = emptyMap()

    init {
        Schedulers.io().scheduleDirect {

            val json = context.resources.openRawResource(R.raw.prevalence).bufferedReader().use { it.readText() }
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Double>>(mapType)
            data = adapter.fromJson(json)
        }
    }

    override fun findPrevalenceOf(entity: String): Double? {
        return data[entity]
    }

}