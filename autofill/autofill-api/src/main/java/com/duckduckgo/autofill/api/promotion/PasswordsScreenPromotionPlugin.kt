/*
 * Copyright (c) 2024 DuckDuckGo
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
 * Used for displaying promotions in the passwords screen.
 * The password management view supports an area in the screen that can be used for different purposes, such as promotions or surveys.
 */
interface PasswordsScreenPromotionPlugin {

    /**
     * Returns a view to be displayed in the passwords screen, or null if the promotion should not be shown.
     * @param context The context to use to inflate the view.
     * @param numberSavedPasswords The number of saved passwords.
     * @return Some promotions may require criteria to be met before they are shown. If the criteria is not met, this method should return null.
     */
    suspend fun getView(context: Context, numberSavedPasswords: Int): View?

    /**
     * Callback for interactions with the promotion.
     */
    interface Callback {
        fun onPromotionDismissed()
    }

    companion object {
        const val PRIORITY_KEY_IMPORT_PROMO = 50
        const val PRIORITY_KEY_SURVEY = 100
        const val PRIORITY_KEY_SYNC_PROMO = 200
    }
}
