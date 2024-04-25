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

package com.duckduckgo.app.browser.viewstate

import com.duckduckgo.app.browser.favorites.NewTabSectionsAdapter
import com.duckduckgo.app.browser.favorites.NewTabSectionsItem
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.remote.messaging.api.RemoteMessage

data class CtaViewState(
    val cta: Cta? = null,
    val message: RemoteMessage? = null,
    val favorites: List<NewTabSectionsItem.FavouriteItem> = emptyList(),
    val shortcuts: List<NewTabSectionsItem.ShortcutItem> = NewTabSectionsAdapter.SHORTCUTS,
)
