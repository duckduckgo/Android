/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import androidx.annotation.StringRes
import com.duckduckgo.mobile.android.vpn.R

sealed class ReportBreakageCategory(@StringRes val category: Int, val key: String) {
    object CrashesCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryCrashes, CRASHES_CATEGORY_KEY)
    object MessagesCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryMessages, MESSAGES_CATEGORY_KEY)
    object CallsCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryCalls, CALLS_CATEGORY_KEY)
    object UploadsCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryUploads, UPLOADS_CATEGORY_KEY)
    object DownloadsCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryDownloads, DOWNLOADS_CATEGORY_KEY)
    object ContentCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryContent, CONTENT_CATEGORY_KEY)
    object ConnectionCategory :
        ReportBreakageCategory(
            R.string.atp_ReportBreakageCategoryConnection,
            CONNECTION_CATEGORY_KEY,
        )
    object IotCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryIot, IOT_CATEGORY_KEY)
    object OtherCategory :
        ReportBreakageCategory(R.string.atp_ReportBreakageCategoryOther, OTHER_CATEGORY_KEY)

    override fun toString(): String {
        return key
    }

    companion object {
        private const val CRASHES_CATEGORY_KEY = "crashes"
        private const val MESSAGES_CATEGORY_KEY = "messages"
        private const val CALLS_CATEGORY_KEY = "calls"
        private const val UPLOADS_CATEGORY_KEY = "uploads"
        private const val DOWNLOADS_CATEGORY_KEY = "downloads"
        private const val CONTENT_CATEGORY_KEY = "content"
        private const val CONNECTION_CATEGORY_KEY = "connection"
        private const val IOT_CATEGORY_KEY = "iot"
        private const val OTHER_CATEGORY_KEY = "other"
    }
}
