/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.remotemessage

import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.ui.view.MessageCta.Message
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage

fun RemoteMessage.asMessage(): Message {
    return when (val content = this.content) {
        is Small -> Message(
            title = content.titleText,
            subtitle = content.descriptionText
        )
        is BigSingleAction -> Message(
            illustration = content.placeholder.drawable(),
            title = content.titleText,
            subtitle = content.descriptionText,
            action = content.primaryActionText
        )
        is BigTwoActions -> Message(
            illustration = content.placeholder.drawable(),
            title = content.titleText,
            subtitle = content.descriptionText,
            action = content.primaryActionText,
            action2 = content.secondaryActionText
        )
        is Medium -> Message(
            illustration = content.placeholder.drawable(),
            title = content.titleText,
            subtitle = content.descriptionText
        )
    }
}

private fun String.drawable(): Int {
    return R.drawable.set_as_default_browser_illustration_dialog
}
