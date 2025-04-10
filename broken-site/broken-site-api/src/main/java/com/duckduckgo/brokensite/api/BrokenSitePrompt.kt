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

package com.duckduckgo.brokensite.api

import android.net.Uri

interface BrokenSitePrompt {

    suspend fun userDismissedPrompt()

    suspend fun userAcceptedPrompt()

    suspend fun isFeatureEnabled(): Boolean

    fun pageRefreshed(url: Uri)

    fun resetRefreshCount()

    fun getUserRefreshesCount(): Int

    suspend fun shouldShowBrokenSitePrompt(url: String): Boolean

    suspend fun ctaShown()
}
