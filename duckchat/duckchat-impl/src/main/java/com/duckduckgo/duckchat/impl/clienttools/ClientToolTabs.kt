/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.clienttools

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.duckchat.api.DuckChat

/** Whether client tools may see or act on [tab]: web pages only, never a Duck.ai chat. */
internal fun DuckChat.isToolVisible(tab: TabEntity): Boolean {
    val url = tab.url ?: return false
    return url.isHttpOrHttps() && !isDuckChatUrl(url.toUri())
}
