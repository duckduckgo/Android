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

package com.duckduckgo.pagetranslation.impl.menu

import android.content.Context
import android.net.Uri
import android.view.View
import com.duckduckgo.app.browser.menu.TopInContextSection
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import javax.inject.Inject

/**
 * Ordered after the ad blocking row, which only shows on YouTube, until product decides how the two group.
 */
@ContributesIntoMap(AppScope::class)
@PriorityKey(TranslationMenuSection.PRIORITY)
class TranslationMenuSection @Inject constructor() : TopInContextSection {
    override fun getView(url: Uri, context: Context, onClick: () -> Unit): View =
        TranslationMenuRowView(context).also { it.bind(onHostClick = onClick) }

    companion object {
        const val PRIORITY = 200
    }
}
