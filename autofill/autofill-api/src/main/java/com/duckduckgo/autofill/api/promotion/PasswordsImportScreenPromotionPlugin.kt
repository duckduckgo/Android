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

package com.duckduckgo.autofill.api.promotion

import android.content.Context
import android.view.View

/**
 * Lets features show a promo card on the passwords import success screen.
 *
 * The host asks plugins in priority order (lower priority key first, set with @PriorityKey)
 * and shows the card of the first eligible one. At most one card is shown.
 */
interface PasswordsImportScreenPromotionPlugin {

    /**
     * Whether this promo should show for this import.
     *
     * @param data result of the import that just finished
     */
    suspend fun isEligible(data: ImportResult): Boolean

    /**
     * Creates the promo card. Only called after [isEligible] returned true.
     * Any ongoing work in the card should be tied to the returned view
     * (start on attach, stop on detach).
     *
     * @param context context to inflate the card with
     * @param callback lets the card tell the host what the user did
     */
    fun createView(
        context: Context,
        callback: Callback,
    ): View

    /**
     * @param successCount number of passwords imported
     * @param skippedCount number of passwords skipped as duplicates or invalid
     */
    data class ImportResult(
        val successCount: Int,
        val skippedCount: Int,
    )

    interface Callback {
        /**
         * The host closes the import screen when this is called.
         */
        fun dismissHost()
    }

    companion object {
        const val PRIORITY_KEY_IMPORT_BOOKMARKS_PROMO = 100
        const val PRIORITY_KEY_SYNC_PROMO = 200
    }
}
