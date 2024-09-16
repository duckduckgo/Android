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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.commands.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.databinding.NavigationHistoryPopupViewBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.history.NavigationHistoryAdapter.NavigationHistoryListener
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class NavigationHistorySheet(
    context: Context,
    private val viewLifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val tabId: String,
    private val history: ShowBackNavigationHistory,
    private val listener: NavigationHistorySheetListener,
) : BottomSheetDialog(context) {

    private val binding = NavigationHistoryPopupViewBinding.inflate(LayoutInflater.from(context))

    interface NavigationHistorySheetListener {
        fun historicalPageSelected(stackIndex: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.historyRecycler.also { recycler ->
            NavigationHistoryAdapter(
                viewLifecycleOwner,
                faviconManager,
                tabId,
                object : NavigationHistoryListener {
                    override fun historicalPageSelected(stackIndex: Int) {
                        dismiss()
                        listener.historicalPageSelected(stackIndex)
                    }
                },
            ).also { adapter ->
                recycler.adapter = adapter
                adapter.updateNavigationHistory(history.history)
            }
        }
    }
}
