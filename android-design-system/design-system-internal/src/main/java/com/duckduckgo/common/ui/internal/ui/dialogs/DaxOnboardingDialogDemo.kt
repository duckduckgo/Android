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

package com.duckduckgo.common.ui.internal.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButton
import com.duckduckgo.common.ui.compose.onboarding.DaxContextualOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDismiss
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogTail
import com.duckduckgo.common.ui.compose.onboarding.rememberDaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.internal.R
import com.duckduckgo.mobile.android.R as CommonR

/** Standalone Compose demo; the primary CTA is the only action that replaces the content. */
@Composable
internal fun DaxOnboardingDialogDemo(modifier: Modifier = Modifier) {
    var contentNumber by remember { mutableIntStateOf(0) }
    var readyContentKey by remember { mutableStateOf<String?>(null) }
    var primaryEnabled by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var tailNumber by remember { mutableIntStateOf(0) }
    var tailExtended by remember { mutableStateOf(true) }
    val dialogState = rememberDaxOnboardingDialogState()
    val contentKey = "demo-$contentNumber"
    val firstContent = contentNumber % 2 == 0
    val contentReady = readyContentKey == contentKey
    val tails = DaxOnboardingDialogTail.entries
    val context = LocalContext.current
    val dismissedMessage = stringResource(R.string.dsOnboardingDemoDismissed)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DaxText(
            text = stringResource(if (contentReady) R.string.dsOnboardingDemoReady else R.string.dsOnboardingDemoIntro),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton(
                text = stringResource(R.string.dsOnboardingDemoSkip),
                onClick = dialogState::skip,
                modifier = Modifier.weight(1f),
                enabled = dialogState.isAnimating,
            )
            DemoButton(
                text = stringResource(if (primaryEnabled) R.string.dsOnboardingDemoDisable else R.string.dsOnboardingDemoEnable),
                onClick = { primaryEnabled = !primaryEnabled },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton(
                text = stringResource(R.string.dsOnboardingDemoTail),
                onClick = { tailNumber = (tailNumber + 1) % tails.size },
                modifier = Modifier.weight(1f),
            )
            DemoButton(
                text = stringResource(if (tailExtended) R.string.dsOnboardingDemoRetract else R.string.dsOnboardingDemoExtend),
                onClick = { tailExtended = !tailExtended },
                modifier = Modifier.weight(1f),
            )
        }
        DemoButton(
            text = stringResource(if (expanded) R.string.dsOnboardingDemoLess else R.string.dsOnboardingDemoMore),
            onClick = { expanded = !expanded },
            enabled = contentReady,
            modifier = Modifier.fillMaxWidth(),
        )

        OnboardingTheme {
            DaxContextualOnboardingDialog(
                contentKey = contentKey,
                title = stringResource(if (firstContent) R.string.dsOnboardingDemoTitleFirst else R.string.dsOnboardingDemoTitleSecond),
                body = stringResource(if (firstContent) R.string.dsOnboardingDemoBodyFirst else R.string.dsOnboardingDemoBodySecond),
                modifier = Modifier.fillMaxWidth(),
                state = dialogState,
                headerImage = if (firstContent) painterResource(CommonR.drawable.subscription_96) else null,
                tail = tails[tailNumber],
                tailExtended = tailExtended,
                primaryCta = DaxOnboardingDialogCta(
                    text = stringResource(if (firstContent) R.string.dsOnboardingDemoContinue else R.string.dsOnboardingDemoReturn),
                    enabled = primaryEnabled,
                    onClick = {
                        if (contentReady && primaryEnabled) {
                            contentNumber++
                            expanded = false
                        }
                    },
                ),
                dismiss = DaxOnboardingDialogDismiss(stringResource(R.string.dsOnboardingDemoDismiss)) {
                    Toast.makeText(context, dismissedMessage, Toast.LENGTH_SHORT).show()
                },
                onIntroFinished = { readyContentKey = it },
            ) {
                if (expanded) {
                    DaxText(
                        text = stringResource(R.string.dsOnboardingDemoDetails),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxSecondaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, size = DaxButtonSize.Small)
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogDemoPreview() {
    DuckDuckGoTheme {
        DaxOnboardingDialogDemo()
    }
}
