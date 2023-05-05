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

package com.duckduckgo.mobile.android.vpn.di

import android.content.Context
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R.string
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(ActivityScope::class)
object AppTrackerBreakageCategoriesModule {
    @Provides
    @AppTpBreakageCategories
    fun provideAppTrackerBreakageCategories(context: Context): List<AppBreakageCategory> {
        return mutableListOf(
            AppBreakageCategory("crashes", context.getString(string.atp_ReportBreakageCategoryCrashes)),
            AppBreakageCategory("messages", context.getString(string.atp_ReportBreakageCategoryMessages)),
            AppBreakageCategory("calls", context.getString(string.atp_ReportBreakageCategoryCalls)),
            AppBreakageCategory("uploads", context.getString(string.atp_ReportBreakageCategoryUploads)),
            AppBreakageCategory("downloads", context.getString(string.atp_ReportBreakageCategoryDownloads)),
            AppBreakageCategory("content", context.getString(string.atp_ReportBreakageCategoryContent)),
            AppBreakageCategory("connection", context.getString(string.atp_ReportBreakageCategoryConnection)),
            AppBreakageCategory("iot", context.getString(string.atp_ReportBreakageCategoryIot)),
        ).apply {
            shuffle()
            add(AppBreakageCategory("other", context.getString(string.atp_ReportBreakageCategoryOther)))
        }
    }
}
