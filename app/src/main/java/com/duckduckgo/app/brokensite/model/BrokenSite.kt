/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.brokensite.model

import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R

data class BrokenSite(
    val category: String?,
    val description: String?,
    val siteUrl: String,
    val upgradeHttps: Boolean,
    val blockedTrackers: String,
    val surrogates: String,
    val webViewVersion: String,
    val siteType: String,
    val urlParametersRemoved: Boolean,
    val consentManaged: Boolean,
    val consentOptOutFailed: Boolean,
    val consentSelfTestFailed: Boolean,
    val errorCodes: String,
    val httpErrorCodes: String,
    val loginSite: String?,
    val reportFlow: ReportFlow?,
)

sealed class BrokenSiteCategory(
    @StringRes val category: Int,
    val key: String,
) {
    object BlockedCategory : BrokenSiteCategory(R.string.brokenSiteCategoryBlocked, BLOCKED_CATEGORY_KEY)
    object LayoutCategory : BrokenSiteCategory(R.string.brokenSiteCategoryLayout, LAYOUT_CATEGORY_KEY)
    object EmptySpacesCategory : BrokenSiteCategory(R.string.brokenSiteCategoryEmptySpaces, EMPTY_SPACES_CATEGORY_KEY)
    object ShoppingCategory : BrokenSiteCategory(R.string.brokenSiteCategoryShopping, SHOPPING_CATEGORY_KEY)
    object PaywallCategory : BrokenSiteCategory(R.string.brokenSiteCategoryPaywall, PAYWALL_CATEGORY_KEY)
    object CommentsCategory : BrokenSiteCategory(R.string.brokenSiteCategoryComments, COMMENTS_CATEGORY_KEY)
    object VideosCategory : BrokenSiteCategory(R.string.brokenSiteCategoryVideos, VIDEOS_CATEGORY_KEY)
    object LoginCategory : BrokenSiteCategory(R.string.brokenSiteCategoryLogin, LOGIN_CATEGORY_KEY)
    object OtherCategory : BrokenSiteCategory(R.string.brokenSiteCategoryOther, OTHER_CATEGORY_KEY)

    companion object {
        const val BLOCKED_CATEGORY_KEY = "blocked"
        const val LAYOUT_CATEGORY_KEY = "layout"
        const val EMPTY_SPACES_CATEGORY_KEY = "empty-spaces"
        const val SHOPPING_CATEGORY_KEY = "shopping"
        const val PAYWALL_CATEGORY_KEY = "paywall"
        const val COMMENTS_CATEGORY_KEY = "comments"
        const val VIDEOS_CATEGORY_KEY = "videos"
        const val LOGIN_CATEGORY_KEY = "login"
        const val OTHER_CATEGORY_KEY = "other"
    }
}

enum class ReportFlow { DASHBOARD, MENU }
