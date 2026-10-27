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

package com.duckduckgo.app.dev.settings.quiz

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.DaxAction
import com.duckduckgo.common.ui.compose.message.remote.DaxBigSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxBigTwoActionsMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxMediumMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxPromoSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxSmallMessage
import com.duckduckgo.common.ui.compose.snackbar.DaxSnackbar
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_PROMO_MESSAGE
import com.google.android.material.snackbar.Snackbar
import com.duckduckgo.mobile.android.R as DesignSystemR

internal val messagingQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.Snackbar to quizRenderer(
        createView = ::snackbarSample,
        composeSample = {
            DaxSnackbar(
                message = SNACKBAR_TEXT,
                action = DaxAction(text = SNACKBAR_ACTION, onClick = {}),
                modifier = Modifier.padding(SNACKBAR_MARGIN_DP.dp),
            )
        },
    ),
    QuestionId.SmallMessage to messageRenderer(
        message = Message(title = "Small Message", subtitle = "Body text goes here. This component doesn't have buttons"),
        composeSample = {
            DaxSmallMessage(
                title = "Small Message",
                body = "Body text goes here. This component doesn't have buttons",
                onDismissed = {},
                modifier = messageMargin(),
            )
        },
    ),
    QuestionId.MediumMessage to messageRenderer(
        message = Message(
            topIllustration = DesignSystemR.drawable.critical_update_96,
            title = "Medium Message",
            subtitle = "Body text goes here. This component doesn't have buttons",
        ),
        composeSample = {
            DaxMediumMessage(
                title = "Medium Message",
                body = "Body text goes here. This component doesn't have buttons",
                topIllustration = painterResource(DesignSystemR.drawable.critical_update_96),
                onDismissed = {},
                modifier = messageMargin(),
            )
        },
    ),
    QuestionId.BigSingleActionMessage to messageRenderer(
        message = Message(
            topIllustration = DesignSystemR.drawable.announcement_96,
            title = "Big Single Message",
            subtitle = "Body text goes here. This component has one button",
            action = "Primary",
        ),
        composeSample = {
            DaxBigSingleActionMessage(
                topIllustration = painterResource(DesignSystemR.drawable.announcement_96),
                title = "Big Single Message",
                body = "Body text goes here. This component has one button",
                action = DaxAction(text = "Primary", onClick = {}),
                onDismissed = {},
                modifier = messageMargin(),
            )
        },
    ),
    QuestionId.BigTwoActionsMessage to messageRenderer(
        message = Message(
            topIllustration = DesignSystemR.drawable.duckduckgo_96,
            title = "Big Two Actions Message",
            subtitle = "Body text goes here. This component has two buttons",
            action = "Primary",
            action2 = "Secondary",
        ),
        composeSample = {
            DaxBigTwoActionsMessage(
                topIllustration = painterResource(DesignSystemR.drawable.duckduckgo_96),
                title = "Big Two Actions Message",
                body = "Body text goes here. This component has two buttons",
                primaryAction = DaxAction(text = "Primary", onClick = {}),
                secondaryAction = DaxAction(text = "Secondary", onClick = {}),
                onDismissed = {},
                modifier = messageMargin(),
            )
        },
    ),
    QuestionId.PromoSingleActionMessage to messageRenderer(
        message = Message(
            middleIllustration = DesignSystemR.drawable.laptop_ddginstall_96,
            title = "Promo Single Action Message",
            subtitle = "Body text goes here. This component has one promo button",
            promoAction = "Promo Link",
            messageType = REMOTE_PROMO_MESSAGE,
        ),
        composeSample = {
            DaxPromoSingleActionMessage(
                title = "Promo Single Action Message",
                body = "Body text goes here. This component has one promo button",
                illustration = painterResource(DesignSystemR.drawable.laptop_ddginstall_96),
                illustrationContentDescription = null,
                action = DaxAction(text = "Promo Link", onClick = {}),
                onDismissed = {},
                modifier = messageMargin(),
            )
        },
    ),
)

private const val SNACKBAR_TEXT = "This is a Snackbar message"
private const val SNACKBAR_ACTION = "Action"
private const val SNACKBAR_MARGIN_DP = 8

@Composable
private fun messageMargin(): Modifier = Modifier.padding(dimensionResource(DesignSystemR.dimen.keyline_4))

private fun messageRenderer(message: Message, composeSample: @Composable () -> Unit): QuizRenderer = quizRenderer(
    createView = { context -> MessageCta(context).apply { setMessage(message) } },
    composeSample = composeSample,
)

private fun snackbarSample(context: Context): View {
    val container = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
    }
    val snackbarView = Snackbar.make(container, SNACKBAR_TEXT, Snackbar.LENGTH_INDEFINITE)
        .setAction(SNACKBAR_ACTION) {}
        .view
    (snackbarView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
    container.addView(snackbarView)
    return container
}
