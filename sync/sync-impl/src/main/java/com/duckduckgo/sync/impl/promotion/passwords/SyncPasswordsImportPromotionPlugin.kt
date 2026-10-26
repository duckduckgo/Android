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

package com.duckduckgo.sync.impl.promotion.passwords

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Companion.PRIORITY_KEY_SYNC_PROMO
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.ImportResult
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesIntoMap(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_SYNC_PROMO)
class SyncPasswordsImportPromotionPlugin @Inject constructor() : PasswordsImportScreenPromotionPlugin {

    override suspend fun isEligible(data: ImportResult): Boolean = true

    override fun createView(
        context: Context,
        callback: Callback,
    ): View {
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, 160.toPx(context))
            setBackgroundColor(context.getColorFromAttr(CommonR.attr.daxColorAccentBlue))
            addView(
                DaxTextView(context).apply {
                    text = "Dummy plugin"
                    setTextColor(Color.WHITE)
                },
                FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER),
            )
            setOnClickListener { callback.dismissHost() }
        }
    }
}
