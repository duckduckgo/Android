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

package com.duckduckgo.user.website.blocklist.impl.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.duckduckgo.user.website.blocklist.impl.R

class UserBlockedSiteWarningLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    sealed class Action {
        data class UnblockAndReload(val domain: String) : Action()
        data object GoBack : Action()
    }

    init {
        View.inflate(context, R.layout.view_user_blocked_site_warning, this)
    }

    fun bind(domain: String, onAction: (Action) -> Unit) {
        findViewById<TextView>(R.id.userBlockedBody).text =
            context.getString(R.string.userBlockedSiteBody, domain)
        findViewById<View>(R.id.userBlockedUnblockBtn).setOnClickListener {
            onAction(Action.UnblockAndReload(domain))
        }
        findViewById<View>(R.id.userBlockedGoBackBtn).setOnClickListener {
            onAction(Action.GoBack)
        }
    }
}
