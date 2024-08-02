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

package com.duckduckgo.autofill.impl.ui.credential.saving.declines

interface AutofillDeclineStore {
    /**
     * Whether to monitor autofill decline counts or not
     * Used to determine whether we should actively detect when a user new to autofill doesn't appear to want it enabled
     */
    var monitorDeclineCounts: Boolean

    /**
     * A count of the number of autofill declines the user has made, persisted across all sessions.
     * Used to determine whether we should prompt a user new to autofill to disable it if they don't appear to want it enabled
     */
    var autofillDeclineCount: Int
}
