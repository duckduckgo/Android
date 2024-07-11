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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.BOOKMARK_LAUNCHED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.BOOKMARK_MENU_ADD_FAVORITE_CLICKED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.BOOKMARK_MENU_DELETE_BOOKMARK_CLICKED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.BOOKMARK_MENU_EDIT_BOOKMARK_CLICKED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.BOOKMARK_MENU_REMOVE_FAVORITE_CLICKED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CONFIRMED
import com.duckduckgo.savedsites.impl.SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SavedSitesPixelsParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {

    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            BOOKMARK_LAUNCHED.pixelName to PixelParameter.removeAtb(),
            EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED.pixelName to PixelParameter.removeAtb(),
            EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED.pixelName to PixelParameter.removeAtb(),
            EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED.pixelName to PixelParameter.removeAtb(),
            EDIT_BOOKMARK_DELETE_BOOKMARK_CONFIRMED.pixelName to PixelParameter.removeAtb(),
            EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED.pixelName to PixelParameter.removeAtb(),
            BOOKMARK_MENU_ADD_FAVORITE_CLICKED.pixelName to PixelParameter.removeAtb(),
            BOOKMARK_MENU_EDIT_BOOKMARK_CLICKED.pixelName to PixelParameter.removeAtb(),
            BOOKMARK_MENU_REMOVE_FAVORITE_CLICKED.pixelName to PixelParameter.removeAtb(),
            BOOKMARK_MENU_DELETE_BOOKMARK_CLICKED.pixelName to PixelParameter.removeAtb(),
        )
    }
}
