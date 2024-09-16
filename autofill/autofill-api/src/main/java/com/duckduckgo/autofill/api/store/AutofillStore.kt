/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.api.store

import com.duckduckgo.autofill.api.domain.app.LoginCredentials

/**
 * Public APIs for querying credentials stored in the autofill store
 */
interface AutofillStore {

    /**
     * Find saved credentials for the given URL, returning an empty list where no matches are found
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     */
    suspend fun getCredentials(rawUrl: String): List<LoginCredentials>
}
