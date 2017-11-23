/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.duckduckgo.app.browser.BrowserViewModel
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.privacydashboard.PrivacyDashboardViewModel
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.store.TrackerDataProvider
import javax.inject.Inject


@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
        private val queryUrlConverter: QueryUrlConverter,
        private val trackerDataProvier: TrackerDataProvider,
        private val trackerDetector: TrackerDetector,
        private val trackerListService: TrackerListService
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>) =
            with(modelClass) {
                when {
                    isAssignableFrom(BrowserViewModel::class.java) -> BrowserViewModel(queryUrlConverter, trackerDataProvier, trackerDetector, trackerListService)
                    isAssignableFrom(PrivacyDashboardViewModel::class.java) -> PrivacyDashboardViewModel()
                    else ->
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
}
