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
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.doOnAttach
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Companion.PRIORITY_KEY_SYNC_PROMO
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.ImportResult
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.promotion.widget.PromoMessageView
import com.duckduckgo.sync.impl.ui.SyncLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import dev.zacsweers.metro.ContributesIntoMap
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesIntoMap(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_SYNC_PROMO)
class SyncPasswordsImportPromotionPlugin @Inject constructor(
    private val promotion: PasswordsImportSyncPromotion,
    private val globalActivityStarter: GlobalActivityStarter,
) : PasswordsImportScreenPromotionPlugin {

    override suspend fun isEligible(data: ImportResult): Boolean {
        return promotion.canShowPromotion(data.successCount)
    }

    override fun createView(
        context: Context,
        callback: Callback,
    ): View {
        return PromoMessageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            headerImage = R.drawable.desktop_mobile_sync_96
            title = context.getString(R.string.sync_passwords_import_promo_title)
            primaryButtonText = context.getString(R.string.sync_passwords_import_promo_primary_button)
            showCloseButton = false
            showElevation = false
            cardColor = context.getColorFromAttr(CommonR.attr.daxColorSurface)
            doOnAttach { promotion.onPromotionDisplayed() }
            onPrimaryButtonClicked {
                promotion.onPromotionConfirmed()
                globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = SyncLaunchSource.PromotionPasswordsImport.value))
                callback.dismissHost()
            }
        }
    }
}
