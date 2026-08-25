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
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import javax.inject.Inject

/**
 * Offers turning Duck.ai on or off, and commits the pick as the user's Duck.ai setting, the same
 * setting the Duck.ai settings screen writes.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingSingleChoiceDataPlugin::class,
    featureName = "pluginOnboardingDuckAiStateDataPluginImpl",
    parentFeatureName = "pluginPointOnboardingSingleChoiceData",
)
class OnboardingDuckAiStateDataPluginImpl @Inject constructor(
    private val context: Context,
    private val duckChat: DuckChatInternal,
) : OnboardingSingleChoiceDataPlugin {

    override val id: Id = Id.DuckAiState

    override suspend fun prefetch() {
        // The options are static, so there is nothing to warm up.
    }

    override suspend fun options(): List<Option> = OFFERED.map { it.toOption() }

    override suspend fun apply(option: Option) {
        val enabled = (option as? StateOption)?.enabled ?: return
        duckChat.setEnableDuckChatUserSetting(enabled)
    }

    private fun StateSpec.toOption() = StateOption(
        id = id,
        label = context.getString(labelRes),
        enabled = enabled,
    )

    private data class StateSpec(
        val id: String,
        val enabled: Boolean,
        @field:StringRes val labelRes: Int,
    )

    private data class StateOption(
        override val id: String,
        override val label: String,
        override val iconRes: Int? = null,
        val enabled: Boolean,
    ) : Option

    private companion object {
        /** Display order, first entry is the default. Ids are the step's pixel values, so they must not change. */
        val OFFERED = listOf(
            StateSpec(
                id = "duck_ai_on",
                enabled = true,
                labelRes = R.string.duckChatOnboardingDuckAiStateOn,
            ),
            StateSpec(
                id = "duck_ai_off",
                enabled = false,
                labelRes = R.string.duckChatOnboardingDuckAiStateOff,
            ),
        )
    }
}
