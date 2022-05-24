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

package com.duckduckgo.app.browser.history

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.history.NavigationHistoryAdapter.NavigationHistoryListener
import com.duckduckgo.mobile.android.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class NavigationHistorySheet(
    context: Context,
    private val viewLifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val tabId: String,
    private val history: ShowBackNavigationHistory,
    private val listener: NavigationHistorySheetListener
) : BottomSheetDialog(context, R.style.NavigationHistoryDialog) {

    interface NavigationHistorySheetListener {
        fun historicalPageSelected(stackIndex: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true

        setContentView(com.duckduckgo.app.browser.R.layout.navigation_history_popup_view)

        findViewById<RecyclerView>(com.duckduckgo.app.browser.R.id.historyRecycler)?.also { recycler ->
            NavigationHistoryAdapter(
                viewLifecycleOwner, faviconManager, tabId,
                object : NavigationHistoryListener {
                    override fun historicalPageSelected(stackIndex: Int) {
                        dismiss()
                        listener.historicalPageSelected(stackIndex)
                    }
                }
            ).also { adapter ->
                recycler.adapter = adapter
                adapter.updateNavigationHistory(history.history)
            }
        }
    }
}
