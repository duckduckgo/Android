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

package com.duckduckgo.duckchat.impl.onboarding

import android.content.Context
import androidx.annotation.StringRes
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Offers the position the input mode toggle opens in on a new tab, and commits the pick as the
 * user's default toggle position, the same setting the Duck.ai settings screen writes.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingSingleChoiceDataPlugin::class,
    featureName = "pluginOnboardingDuckAiNewTabTogglePositionDataPluginImpl",
    parentFeatureName = "pluginPointOnboardingSingleChoiceData",
)
class OnboardingDuckAiNewTabTogglePositionDataPluginImpl @Inject constructor(
    private val context: Context,
    private val duckChat: DuckChatInternal,
) : OnboardingSingleChoiceDataPlugin {

    override val id: Id = Id.DuckAiNewTabTogglePosition

    override suspend fun options(): List<Option> = OFFERED.map { it.toOption() }

    override suspend fun apply(option: Option) {
        val position = (option as? PositionOption)?.position
        if (position == null) {
            logcat(WARN) { "Duck.ai onboarding: ignoring toggle position pick from a foreign option ${option.id}" }
            return
        }
        duckChat.setDefaultTogglePosition(position)
    }

    private fun PositionSpec.toOption() = PositionOption(
        id = position.pixelValue,
        label = context.getString(labelRes),
        position = position,
    )

    private data class PositionSpec(
        val position: DefaultTogglePosition,
        @field:StringRes val labelRes: Int,
    )

    private data class PositionOption(
        override val id: String,
        override val label: String,
        override val iconRes: Int? = null,
        val position: DefaultTogglePosition,
    ) : Option

    private companion object {
        /**
         * Display order, first entry is the default. Ids come from the position's pixel value, so the
         * step reports the same values the Duck.ai settings screen does.
         */
        val OFFERED = listOf(
            PositionSpec(
                position = DefaultTogglePosition.DUCK_AI,
                labelRes = R.string.duckChatOnboardingNewTabTogglePositionDuckAi,
            ),
            PositionSpec(
                position = DefaultTogglePosition.LAST_USED,
                labelRes = R.string.duckChatOnboardingNewTabTogglePositionLastUsed,
            ),
        )
    }
}
