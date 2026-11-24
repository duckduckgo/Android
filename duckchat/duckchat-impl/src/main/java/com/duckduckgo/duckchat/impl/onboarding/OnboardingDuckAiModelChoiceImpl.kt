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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckAiModelProviderFeature
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.onboarding.api.OnboardingDuckAiModelChoice
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Starts the onboarding chat on the picked model without touching the user's model selection. The model
 * rides on the pending native prompt, the same way the native input sends a new chat's model to Duck.ai.
 */
@ContributesBinding(AppScope::class)
class OnboardingDuckAiModelChoiceImpl @Inject constructor(
    private val context: Context,
    private val duckChat: DuckChat,
    private val duckAiModelManager: DuckAiModelManager,
    private val duckAiModelProviderFeature: DuckAiModelProviderFeature,
    private val pendingNativePromptStore: PendingNativePromptStore,
) : OnboardingDuckAiModelChoice {

    override suspend fun prefetch() {
        duckAiModelManager.fetchModels()
    }

    override suspend fun options(): List<Option> {
        val offered = duckAiModelManager.modelState.value.models
            .filter { it.requiredTier == UserTier.FREE }
            .mapNotNull { model -> SUPPORTED.firstOrNull { it.matches(model) } }
            .distinct()

        return offered.ifEmpty { fallbackChoices() }.map { it.toOption() }
    }

    override suspend fun chatUrl(prompt: String, option: Option?): String {
        val model = (option as? ModelOption)?.let { resolveModel(it.choice) }
        if (model != null) {
            // The user never saw the Duck.ai terms in onboarding, so this prompt must not accept them.
            pendingNativePromptStore.store(prompt, model.id, reasoningEffort = null, carriesTermsConsent = false)
        } else if (option != null) {
            logcat(WARN) { "Duck.ai onboarding: no accessible model for ${option.id}, starting on the default" }
        }
        return duckChat.getDuckChatUrl(prompt, autoPrompt = true)
    }

    /** The options may have come from the fallback, before the models response landed. */
    private suspend fun resolveModel(choice: ModelChoice): AIChatModel? {
        if (duckAiModelManager.modelState.value.models.isEmpty()) duckAiModelManager.fetchModels()
        return duckAiModelManager.modelState.value.models.firstOrNull { it.isAccessible && choice.matches(it) }
    }

    /**
     * The choice is rendered early in onboarding, so the models response may not have landed yet.
     */
    private fun fallbackChoices(): List<ModelChoice> {
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

    private fun ModelChoice.toOption() = ModelOption(
        id = id,
        label = context.getString(labelRes),
        iconRes = iconRes,
        choice = this,
    )

    private data class ModelChoice(
        /** Doubles as the pixel value for the step, so it must not change once shipped. */
        val id: String,
        @field:StringRes val labelRes: Int,
        @field:DrawableRes val iconRes: Int,
        val matches: (AIChatModel) -> Boolean,
    )

    private data class ModelOption(
        override val id: String,
        override val label: String,
        @get:DrawableRes override val iconRes: Int,
        val choice: ModelChoice,
    ) : Option

    private companion object {
        const val PROVIDERS_SETTING = "providers"

        /** Display order, first entry is the default. Matches the order the models response returns today. */
        val SUPPORTED = listOf(
            ModelChoice(
                id = "openai",
                labelRes = R.string.duckChatModelProviderOpenAi,
                iconRes = CommonR.drawable.ai_model_openai_24,
                matches = { it.provider == ModelProvider.OPENAI },
            ),
            ModelChoice(
                id = "anthropic",
                labelRes = R.string.duckChatModelProviderAnthropic,
                iconRes = CommonR.drawable.ai_model_claude_24,
                matches = { it.provider == ModelProvider.ANTHROPIC },
            ),
            // Gemma shares the OSS provider with other open models, so it is matched by id.
            ModelChoice(
                id = "gemma",
                labelRes = R.string.duckChatModelGemma,
                iconRes = CommonR.drawable.ai_model_oss_24,
                matches = { it.provider == ModelProvider.OSS && it.id.contains("gemma", ignoreCase = true) },
            ),
        )
    }
}
