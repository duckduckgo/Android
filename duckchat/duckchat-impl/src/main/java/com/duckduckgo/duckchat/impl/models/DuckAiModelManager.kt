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

package com.duckduckgo.duckchat.impl.models

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.duckchat.impl.store.SelectedModel
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

data class ModelState(
    val models: List<AIChatModel> = emptyList(),
    val selectedModelId: String? = null,
    val selectedModelShortName: String? = null,
    val userTier: UserTier = UserTier.FREE,
    val attachmentLimits: AttachmentLimits = AttachmentLimits(),
)

interface DuckAiModelManager {
    val modelState: StateFlow<ModelState>

    suspend fun fetchModels()

    suspend fun selectModel(model: AIChatModel)

    fun getSelectedModelId(): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiModelManager @Inject constructor(
    private val modelsService: DuckAiModelsService,
    private val dataStore: DuckChatDataStore,
    private val subscriptions: Subscriptions,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiModelManager {

    private val _modelState = MutableStateFlow(ModelState())
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            restoreCachedSelection()
            subscriptions.getEntitlementStatus()
                .distinctUntilChanged()
                .collect {
                    logcat { "Duck.ai Model Manager: entitlements changed, re-fetching models" }
                    fetchModels()
                }
        }
    }

    private suspend fun restoreCachedSelection() {
        val cached = dataStore.getSelectedModel() ?: return
        _modelState.value = _modelState.value.copy(
            selectedModelId = cached.id,
            selectedModelShortName = cached.shortName,
        )
    }

    override suspend fun fetchModels() {
        withContext(dispatcherProvider.io()) {
            try {
                val userTier = resolveUserTier()
                val response = fetchModelsResponse()
                val models = response.models.map { resolveModel(it, userTier) }
                val attachmentLimits = resolveAttachmentLimits(response.attachmentLimits, userTier)
                val selectedModelId = validateAndPersistSelection(models)

                _modelState.value = ModelState(
                    models = models,
                    selectedModelId = selectedModelId,
                    selectedModelShortName = models.find { it.id == selectedModelId }?.shortName,
                    userTier = userTier,
                    attachmentLimits = attachmentLimits,
                )
                logcat { "Duck.ai Model Manager: fetched ${models.size} models, tier=$userTier, selected=$selectedModelId" }
            } catch (e: Exception) {
                logcat { "Duck.ai Model Manager: failed to fetch models: ${e.message}" }
            }
        }
    }

    private suspend fun fetchModelsResponse(): AIChatModelsResponse {
        val url = DuckAiModelsService.modelsUrl(duckAiHostProvider.getHost())
        return modelsService.getModels(url)
    }

    private suspend fun validateAndPersistSelection(models: List<AIChatModel>): String? {
        val currentSelectedId = dataStore.getSelectedModel()?.id
        val validatedSelectedId = validateSelection(currentSelectedId, models)

        if (validatedSelectedId != currentSelectedId) {
            val validatedModel = models.find { it.id == validatedSelectedId }
            if (validatedModel != null) {
                dataStore.setSelectedModel(SelectedModel(validatedModel.id, validatedModel.shortName))
            } else {
                dataStore.setSelectedModel(null)
            }
        }
        return validatedSelectedId
    }

    override suspend fun selectModel(model: AIChatModel) {
        withContext(dispatcherProvider.io()) {
            dataStore.setSelectedModel(SelectedModel(model.id, model.shortName))
            _modelState.value = _modelState.value.copy(
                selectedModelId = model.id,
                selectedModelShortName = model.shortName,
            )
            logcat { "Duck.ai Model Manager: selected model ${model.id} (${model.shortName})" }
        }
    }

    override fun getSelectedModelId(): String? = _modelState.value.selectedModelId

    private suspend fun resolveUserTier(): UserTier {
        return try {
            val status = subscriptions.getSubscriptionStatus()
            if (!status.isActiveOrWaiting()) return UserTier.FREE

            val products = subscriptions.getAvailableProducts()
            when {
                products.contains(Product.DuckAiPlus) -> UserTier.PLUS
                else -> UserTier.FREE
            }
        } catch (e: Exception) {
            logcat { "Duck.ai Model Manager: failed to resolve user tier, defaulting to FREE: ${e.message}" }
            UserTier.FREE
        }
    }

    private fun resolveAttachmentLimits(
        remoteLimits: Map<String, RemoteTierAttachmentLimits>?,
        userTier: UserTier,
    ): AttachmentLimits {
        if (remoteLimits.isNullOrEmpty()) return AttachmentLimits()
        val tierLimits = remoteLimits[userTier.rawValue] ?: return AttachmentLimits()
        return AttachmentLimits(
            images = tierLimits.images?.let { remote ->
                ImageLimits(
                    maxPerTurn = remote.maxPerTurn ?: ImageLimits.DEFAULT_IMAGE_MAX_PER_TURN,
                    maxPerConversation = remote.maxPerConversation ?: ImageLimits.DEFAULT_IMAGE_MAX_PER_CONVERSATION,
                    maxInputCharsWithAttachments = remote.maxInputCharsWithAttachments
                        ?: ImageLimits.DEFAULT_MAX_INPUT_CHARS_WITH_ATTACHMENTS,
                )
            } ?: ImageLimits(),
        )
    }

    private fun resolveModel(remote: RemoteAIChatModel, userTier: UserTier): AIChatModel {
        val accessTier = remote.accessTier.orEmpty()
        val isAccessible = if (accessTier.isEmpty()) {
            remote.entityHasAccess
        } else {
            accessTier.contains(userTier.rawValue)
        }
        return AIChatModel(
            id = remote.id,
            name = remote.name,
            displayName = remote.displayName ?: remote.name,
            shortName = remote.shortName ?: remote.name,
            accessTier = accessTier,
            isAccessible = isAccessible,
            provider = ModelProvider.from(id = remote.id, providerString = remote.provider),
            supportsImageUpload = remote.supportsImageUpload,
            supportedImageFormats = if (remote.supportsImageUpload) AIChatModel.NATIVE_SUPPORTED_IMAGE_FORMATS else emptyList(),
        )
    }

    private fun validateSelection(
        currentSelectedId: String?,
        models: List<AIChatModel>,
    ): String? {
        if (currentSelectedId == null) return models.firstOrNull { it.isAccessible }?.id

        val selectedModel = models.find { it.id == currentSelectedId }
        if (selectedModel == null || !selectedModel.isAccessible) {
            return models.firstOrNull { it.isAccessible }?.id
        }
        return currentSelectedId
    }
}

private fun com.duckduckgo.subscriptions.api.SubscriptionStatus.isActiveOrWaiting(): Boolean {
    return this == com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
}
