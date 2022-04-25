/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.jsbridge.request

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.IllegalArgumentException
import javax.inject.Inject

class AutofillRequestParser @Inject constructor(val moshi: Moshi) {

    suspend fun parseRequest(requestString: String): AutofillDataRequest {
        return withContext(Dispatchers.Default) {
            val adapter = moshi.adapter(AutofillDataRequest::class.java)
            return@withContext adapter.fromJson(requestString) ?: throw IllegalArgumentException("Failed to parse autofill request")
        }
    }
}
