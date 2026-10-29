/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.promotion.bookmarks

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.promotion.BookmarksScreenPromotionPlugin
import com.duckduckgo.sync.impl.databinding.ViewSyncBookmarksMultiDevicePromotionBinding
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(scope = AppScope::class)
class SyncBookmarksMultiDevicePromotionPlugin @Inject constructor(
    private val promotion: SyncBookmarksMultiDevicePromotion,
) : BookmarksScreenPromotionPlugin {

    override suspend fun getView(context: Context, numberSavedBookmarks: Int): View? {
        return if (promotion.canShowPromotion(numberSavedBookmarks)) {
            SyncBookmarksMultiDevicePromotionView(context)
        } else {
            null
        }
    }
}

class SyncBookmarksMultiDevicePromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val binding: ViewSyncBookmarksMultiDevicePromotionBinding by viewBinding()
}
