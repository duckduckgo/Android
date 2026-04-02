/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import android.content.Context
import android.view.View
import com.duckduckgo.browser.api.ui.BrowserMenuPlugin
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadsScreens.DownloadsScreenNoParams
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DownloadsMenuItemPlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val downloadMenuStateProvider: DownloadMenuStateProvider,
) : BrowserMenuPlugin {

    override fun getMenuItemView(context: Context): View {
        return MenuItemView(context).apply {
            label(context.getString(R.string.downloadsMenuLabel))
            setIcon(CommonR.drawable.ic_downloads_24)
            setSize(com.duckduckgo.common.ui.view.MenuItemViewSize.MEDIUM)
            showDotIndicator = downloadMenuStateProvider.hasNewDownload()
            setOnClickListener {
                globalActivityStarter.start(context, DownloadsScreenNoParams)
            }
        }
    }

    override val menuHighlightFlow: Flow<Boolean> = downloadMenuStateProvider.hasNewDownloadFlow
}
