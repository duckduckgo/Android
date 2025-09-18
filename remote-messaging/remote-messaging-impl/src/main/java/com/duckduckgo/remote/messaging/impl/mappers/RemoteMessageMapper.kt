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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType
import com.duckduckgo.mobile.android.R
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.Content.Placeholder.ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.APP_UPDATE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.CRITICAL_UPDATE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.DDG_ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.DUCK_AI
import com.duckduckgo.remote.messaging.api.Content.Placeholder.DUCK_AI_OLD
import com.duckduckgo.remote.messaging.api.Content.Placeholder.MAC_AND_WINDOWS
import com.duckduckgo.remote.messaging.api.Content.Placeholder.PRIVACY_SHIELD
import com.duckduckgo.remote.messaging.api.Content.Placeholder.VISUAL_DESIGN_UPDATE
import com.duckduckgo.remote.messaging.api.Content.PromoSingleAction
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage

fun RemoteMessage.asMessage(isLightModeEnabled: Boolean): Message {
    return when (val content = this.content) {
        is Small -> Message(
            title = content.titleText,
            subtitle = content.descriptionText,
            messageType = MessageType.REMOTE_MESSAGE,
        )
        is BigSingleAction -> Message(
            topIllustration = content.placeholder.drawable(isLightModeEnabled),
            title = content.titleText,
            subtitle = content.descriptionText,
            action = content.primaryActionText,
            messageType = MessageType.REMOTE_MESSAGE,
        )
        is BigTwoActions -> Message(
            topIllustration = content.placeholder.drawable(isLightModeEnabled),
            title = content.titleText,
            subtitle = content.descriptionText,
            action = content.primaryActionText,
            action2 = content.secondaryActionText,
            messageType = MessageType.REMOTE_MESSAGE,
        )
        is Medium -> Message(
            topIllustration = content.placeholder.drawable(isLightModeEnabled),
            title = content.titleText,
            subtitle = content.descriptionText,
            messageType = MessageType.REMOTE_MESSAGE,
        )
        is PromoSingleAction -> Message(
            middleIllustration = content.placeholder.drawable(isLightModeEnabled),
            title = content.titleText,
            subtitle = content.descriptionText,
            promoAction = content.actionText,
            messageType = MessageType.REMOTE_PROMO_MESSAGE,
        )
    }
}

private fun Placeholder.drawable(isLightModeEnabled: Boolean): Int {
    return when (this) {
        ANNOUNCE -> R.drawable.ic_announce
        DDG_ANNOUNCE -> R.drawable.ic_ddg_announce
        CRITICAL_UPDATE -> R.drawable.ic_critical_update
        APP_UPDATE -> R.drawable.ic_app_update
        MAC_AND_WINDOWS -> R.drawable.desktop_promo_artwork
        PRIVACY_SHIELD -> R.drawable.ic_privacy_pro
        DUCK_AI_OLD -> R.drawable.ic_duck_ai
        DUCK_AI -> R.drawable.ic_duckai
        VISUAL_DESIGN_UPDATE -> if (isLightModeEnabled) {
            R.drawable.ic_visual_design_update_artwork_light
        } else {
            R.drawable.ic_visual_design_update_artwork_dark
        }
    }
}
