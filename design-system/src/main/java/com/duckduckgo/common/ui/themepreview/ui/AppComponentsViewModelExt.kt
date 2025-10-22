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

package com.duckduckgo.common.ui.themepreview.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.common.ui.store.ThemingSharedPreferences
import com.duckduckgo.common.ui.themepreview.ui.store.AppComponentsPrefsDataStore
import com.duckduckgo.common.ui.themepreview.ui.store.appComponentsDataStore
import com.duckduckgo.common.utils.DefaultDispatcherProvider

internal fun Fragment.appComponentsViewModel(): Lazy<AppComponentsViewModel> = activityViewModels {
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppComponentsViewModel(
                AppComponentsPrefsDataStore(
                    dispatcherProvider = DefaultDispatcherProvider(),
                    context = requireContext(),
                    store = requireContext().appComponentsDataStore,
                    themePrefMapper = ThemingSharedPreferences.ThemePrefsMapper(),
                ),
            ) as T
        }
    }
}
