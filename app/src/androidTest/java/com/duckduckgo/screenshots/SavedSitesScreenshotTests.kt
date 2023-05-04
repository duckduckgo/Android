/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.screenshots

import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.dropbox.dropshots.Dropshots
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import org.junit.Rule
import org.junit.Test
import java.util.*

class SavedSitesScreenshotTests {

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(BookmarksActivity::class.java)

    @get:Rule
    val dropshots = Dropshots()

    @Test
    fun whenSavedSitesEmptyStateMatchesActivityScreenshot() {
        activityScenarioRule.scenario.onActivity {
            val viewState = BookmarksViewModel.ViewState(
                false, emptyList(), emptyList(), emptyList(),
            )
            it.renderViewState(SavedSitesNames.BOOMARKS_ROOT, viewState)
            dropshots.assertSnapshot(it, "SavedSites_Empty")
        }
    }

    @Test @ScreenshotTest
    fun whenSavedSitesWithContentMatchesActivityScreenshot() {
        activityScenarioRule.scenario.onActivity {
            val viewState = BookmarksViewModel.ViewState(
                false,
                listOf(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        title = "A title",
                        url = "http://example.com",
                        parentId = UUID.randomUUID().toString(),
                    ),
                ),
                listOf(
                    Favorite(
                        id = UUID.randomUUID().toString(),
                        title = "A title",
                        url = "http://example.com",
                        position = 0,
                    ),
                ),
                listOf(
                    BookmarkFolder(
                        id = UUID.randomUUID().toString(),
                        name = "folder",
                        SavedSitesNames.BOOMARKS_ROOT,
                        numBookmarks = 1,
                        numFolders = 1,
                    ),
                ),
            )
            it.renderViewState(SavedSitesNames.BOOMARKS_ROOT, viewState)
            dropshots.assertSnapshot(it, "SavedSites_Content")
        }
    }
}
