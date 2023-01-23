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

package com.duckduckgo.autofill.api

import com.duckduckgo.autofill.api.store.AutofillStore

/**
 * Used to determine if the given credential details exist in the autofill storage
 *
 * There are times when the UI from the main app will need to prompt the user if they want to update saved details.
 * We can only show that prompt if we've first determined there is an existing partial match in need of an update.
 */
interface ExistingCredentialMatchDetector {
    suspend fun determine(currentUrl: String, username: String?, password: String?): AutofillStore.ContainsCredentialsResult
}
