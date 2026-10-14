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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckAiModelProviderFeature
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Offers the Duck.ai model providers to the onboarding provider choice, and commits the pick as a
 * provider preference rather than a model one, so the user keeps following the server-side default.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingSingleChoiceDataPlugin::class,
    featureName = "pluginOnboardingDuckAiModelPickerDataPluginImpl",
    parentFeatureName = "pluginPointOnboardingSingleChoiceData",
)
class OnboardingDuckAiModelPickerDataPluginImpl @Inject constructor(
    private val context: Context,
    private val duckAiModelManager: DuckAiModelManager,
    private val duckAiModelProviderFeature: DuckAiModelProviderFeature,
) : OnboardingSingleChoiceDataPlugin {

    override val id: Id = Id.DuckAiModelProvider

    override suspend fun prefetch() {
        duckAiModelManager.fetchModels()
    }

    override suspend fun options(): List<Option> {
        val offered = duckAiModelManager.modelState.value.models
            .filter { it.requiredTier == UserTier.FREE }
            .map { it.provider }
            .distinct()
            .mapNotNull { provider -> SUPPORTED.firstOrNull { it.provider == provider } }

        return offered.ifEmpty { fallbackProviders() }.map { it.toOption() }
    }

    override suspend fun apply(option: Option) {
        val provider = (option as? ProviderOption)?.provider
        if (provider == null) {
            logcat(WARN) { "Duck.ai onboarding: ignoring provider pick from a foreign option ${option.id}" }
            return
        }
        duckAiModelManager.selectProvider(provider)
    }

    /**
     * The choice is rendered early in onboarding, so the models response may not have landed yet.
     */
    private fun fallbackProviders(): List<ProviderSpec> {
        val ids = remoteFallbackIds() ?: SUPPORTED.map { it.id }
        return ids.mapNotNull { id -> SUPPORTED.firstOrNull { it.id == id } }
    }

    private fun remoteFallbackIds(): List<String>? {
        val settings = duckAiModelProviderFeature.self().getSettings() ?: return null
        return runCatching {
            val providers = JSONObject(settings).optJSONArray(PROVIDERS_SETTING) ?: return null
            (0 until providers.length()).map { providers.getString(it) }
        }.getOrNull()
    }

    private fun ProviderSpec.toOption() = ProviderOption(
        id = id,
        label = context.getString(labelRes),
        iconRes = iconRes,
        provider = provider,
    )

    private data class ProviderSpec(
        val provider: ModelProvider,
        /** Doubles as the pixel value for the step, so it must not change once shipped. */
        val id: String,
        @field:StringRes val labelRes: Int,
        @field:DrawableRes val iconRes: Int,
    )

    private data class ProviderOption(
        override val id: String,
        override val label: String,
        @get:DrawableRes override val iconRes: Int,
        val provider: ModelProvider,
    ) : Option

    private companion object {
        const val PROVIDERS_SETTING = "providers"

        /** Display order, first entry is the default. Matches the order the models response returns today. */
        val SUPPORTED = listOf(
            ProviderSpec(
                provider = ModelProvider.OPENAI,
                id = "openai",
                labelRes = R.string.duckChatModelProviderOpenAi,
                iconRes = CommonR.drawable.ai_model_openai_24,
            ),
            ProviderSpec(
                provider = ModelProvider.ANTHROPIC,
                id = "anthropic",
                labelRes = R.string.duckChatModelProviderAnthropic,
                iconRes = CommonR.drawable.ai_model_claude_24,
            ),
            ProviderSpec(
                provider = ModelProvider.MISTRAL,
                id = "mistral",
                labelRes = R.string.duckChatModelProviderMistral,
                iconRes = CommonR.drawable.ai_model_mistral_24,
            ),
        )
    }
}
