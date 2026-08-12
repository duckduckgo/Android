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

package com.duckduckgo.duckchat.impl.contextual

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatContextual
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckChatContextual @Inject constructor() : DuckChatContextual {

    override fun launch(
        sourceTabId: String,
        anchor: View?,
        onAskAboutPage: () -> Unit,
    ) {
        onAskAboutPage()
    }

    override fun createSheet(tabId: String): Fragment {
        return DuckChatContextualFragment().apply {
            arguments = Bundle().apply {
                putString(DuckChatContextualFragment.KEY_DUCK_AI_CONTEXTUAL_TAB_ID, tabId)
            }
        }
    }
}
