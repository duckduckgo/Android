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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.DuckAiUsageLimitsRepository
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.OverLimitNewChatDefault
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.duckchat.impl.store.SelectedModel
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

data class ModelState(
    val models: List<AIChatModel> = emptyList(),
    val selectedModelId: String? = null,
    val selectedModelShortName: String? = null,
    val userTier: UserTier = UserTier.FREE,
    val isSubscriptionEligible: Boolean = false,
    /** Whether the user can still start a free trial, which changes the gated section's header. */
    val isFreeTrialEligible: Boolean = false,
    val attachmentLimits: AttachmentLimits = AttachmentLimits(),
    /** User's persisted global reasoning mode. Used for new chats. */
    val selectedReasoningMode: ReasoningMode? = null,
    val availableReasoningModes: List<AvailableReasoningMode> = emptyList(),
    /** Reasoning mode for the current chat. Session only (not persisted)
     * Note: if we ever want to preserve picks across unsubmitted chats
     * (User selected a mode but navigated away or switched tab without submission)
     * change this to a bounded Map<String, ReasoningMode> keyed by chatId.
     * */
    val chatScopedReasoningMode: ReasoningMode? = null,
    /** Where a new chat starts while `resetModelForNewChats` is on; null while it is off. */
    val newChatDefault: NewChatDefault? = null,
) {
    /**
     * Models not loaded yet count as supported, so the entry points are not hidden while they fetch.
     */
    fun supportsImageUpload(imageUploadEnabled: Boolean): Boolean =
        imageUploadEnabled && (models.isEmpty() || models.firstOrNull { it.id == selectedModelId }?.supportsImageUpload == true)
}

interface DuckAiModelManager {
    val modelState: StateFlow<ModelState>

    suspend fun fetchModels()

    /** Ignored while `resetModelForNewChats` is on, where the pick belongs to the tab's next chat instead. */
    suspend fun selectModel(model: AIChatModel)

    /**
     * Switches to the first accessible image-capable model unless the selected one already is.
     * Returns false when no such model exists. While `resetModelForNewChats` is on, only reports whether one
     * exists and leaves the selection alone, since the tab picks the model for its next new chat.
     */
    suspend fun ensureImageCapableModelSelected(): Boolean

    /**
     * Pins the user to a provider rather than to one of its models, so they keep following the
     * server-side default within it. Replaces any earlier [selectModel] pick.
     */
    suspend fun selectProvider(provider: ModelProvider)

    /** Ignored while `resetModelForNewChats` is on, like [selectModel]. */
    suspend fun selectReasoningMode(mode: ReasoningMode)

    suspend fun setChatScopedReasoningMode(mode: ReasoningMode?)

    fun getSelectedModelId(): String?

    fun getResolvedReasoningEffort(): String?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiModelManager @Inject constructor(
    private val modelsService: DuckAiModelsService,
    private val dataStore: DuckChatDataStore,
    private val subscriptions: Subscriptions,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val duckChatPixels: Lazy<DuckChatPixels>,
    private val cookiesManager: CookieManagerProvider,
    private val usageLimitsRepository: DuckAiUsageLimitsRepository,
    private val userDefaultModelRepository: UserDefaultModelRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiModelManager {

    private val _modelState = MutableStateFlow(ModelState())
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    // Each public model mutator (fetchModels, selectModel, selectReasoningMode) does a read-write
    // on _modelState and may also write to dataStore. Without a lock, two of them running on
    // Dispatchers.IO at the same time can overlap and leave selectedReasoningMode out of sync
    // with availableReasoningModes. This mutex serializes those operations so each one sees a
    // consistent view of the previous state.
    private val stateMutex = Mutex()
    private var selectionRevision = 0L

    // The new-chat default's inputs, kept so a usage-limits or setting change can re-derive it without a fetch.
    // The models stay in endpoint order so the first-accessible fallback matches a fetch. Guarded by stateMutex.
    private var endpointModels: List<AIChatModel> = emptyList()
    private var remoteNewChatDefault: RemoteNewChatDefault? = null
    private var overLimitDefault: OverLimitNewChatDefault? = null
    private var userDefaultModel: UserDefaultModel? = null

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            try {
                // Separate from the restore below so a failed migration cannot cost the user their selection.
                stateMutex.withLock { clearPinnedDefaultModel() }
            } catch (e: Exception) {
                logcat { "Duck.ai Model Manager: failed to clear pinned default model: ${e.message}" }
            }
            try {
                stateMutex.withLock { restoreCachedSelection() }
            } catch (e: Exception) {
                logcat { "Duck.ai Model Manager: failed to restore cached selection: ${e.message}" }
            }
        }
    }

    // Status as well as entitlements: signing in or out changes whether the response carries
    // model labels, and entitlements stay empty either way when there is no subscription.
    private val subscriptionChangeObserver = appCoroutineScope.launch(dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        combine(
            subscriptions.getEntitlements(),
            subscriptions.getSubscriptionStatusFlow(),
        ) { entitlements, status -> entitlements to status }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                logcat { "Duck.ai Model Manager: subscription state changed, re-fetching models" }
                fetchModels()
            }
    }

    // Started by fetchModels, since the over-limit default is ignored until models load. The usage snapshot is
    // only rewritten when the page runs, so the reset is checked whenever the default is derived, including on each fetch.
    private val overLimitDefaultObserver = appCoroutineScope.launch(dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        if (!resetsModelForNewChats()) return@launch
        usageLimitsRepository.usageLimits(BrowserMode.REGULAR)
            .map { it?.newChatDefault }
            .distinctUntilChanged()
            .collect { overLimit -> applyOverLimitDefault(overLimit) }
    }

    private val userDefaultObserver = appCoroutineScope.launch(dispatcherProvider.io(), start = CoroutineStart.LAZY) {
        userDefaultModelRepository.observe().collect { userDefault -> applyUserDefault(userDefault) }
    }

    /**
     * Earlier versions persisted the inferred default as if the user had picked it, which pinned most
     * of the install base to [PINNED_DEFAULT_MODEL_ID] and stopped later server-side default changes
     * from reaching them. Clearing that one id lets [resolveSelection] derive the current default
     * again. Users who deliberately picked it are reset too, as the stored value records no intent.
     */
    private suspend fun clearPinnedDefaultModel() {
        if (dataStore.hasClearedPinnedDefaultModel()) return
        if (dataStore.getSelectedModel()?.id == PINNED_DEFAULT_MODEL_ID) {
            dataStore.setSelectedModel(null)
            logcat { "Duck.ai Model Manager: cleared pinned default model" }
        }
        dataStore.setClearedPinnedDefaultModel()
    }

    private suspend fun restoreCachedSelection() {
        if (resetsModelForNewChats()) {
            selectHardcodedDefaultWhileModelsMissing()
            return
        }
        val cachedModel = dataStore.getSelectedModel()
        val cachedReasoningRaw = dataStore.getSelectedReasoningMode()
        if (cachedModel == null) {
            // Reasoning is only meaningful when a model is selected; drop any orphan from prefs.
            if (cachedReasoningRaw != null) dataStore.setSelectedReasoningMode(null)
            return
        }
        val parsedReasoning = ReasoningMode.from(cachedReasoningRaw)
        if (cachedReasoningRaw != null && parsedReasoning == null) {
            // Unparseable raw (e.g. mode removed in this version) -> clear it so it does not rot in prefs.
            dataStore.setSelectedReasoningMode(null)
        }
        _modelState.value = _modelState.value.copy(
            selectedModelId = cachedModel.id,
            selectedModelShortName = cachedModel.shortName,
            selectedReasoningMode = parsedReasoning,
        )
    }

    override suspend fun fetchModels() {
        subscriptionChangeObserver.start()
        overLimitDefaultObserver.start()
        withContext(dispatcherProvider.io()) {
            try {
                if (userDefaultModelRepository.isUserDefaultEnabled()) userDefaultObserver.start()
                val userTier = resolveUserTier()
                val response = fetchModelsResponse()
                val isSubscriptionEligible = runCatching {
                    subscriptions.isEligible()
                }.getOrElse {
                    logcat { "Duck.ai Model Manager: failed to resolve purchase eligibility, defaulting to not eligible: ${it.message}" }
                    false
                }
                val isFreeTrialEligible = runCatching {
                    subscriptions.isFreeTrialEligible()
                }.getOrElse {
                    logcat { "Duck.ai Model Manager: failed to resolve free trial eligibility, defaulting to not eligible: ${it.message}" }
                    false
                }
                reportUnknownLabels(response.models)
                val models = response.models
                    .map { resolveModel(it, userTier) }
                    .filterNot { it.accessTier.isEmpty() && !it.isAccessible }
                    .filterNot {
                        !it.isAccessible && !isSubscriptionEligible
                    }
                val attachmentLimits = resolveAttachmentLimits(response.attachmentLimits, userTier)
                val resetForNewChats = resetsModelForNewChats()
                stateMutex.withLock {
                    val fetched = _modelState.value.copy(
                        models = models.sortLabelledFirst(),
                        userTier = userTier,
                        isSubscriptionEligible = isSubscriptionEligible,
                        isFreeTrialEligible = isFreeTrialEligible,
                        attachmentLimits = attachmentLimits,
                    )
                    _modelState.value = if (resetForNewChats) {
                        endpointModels = models
                        remoteNewChatDefault = response.newChatDefault
                        fetched.withNewChatDefault()
                    } else {
                        // Selection is resolved on the endpoint order so the derived default keeps following
                        // it, and only the picker's display order changes.
                        val selectedModelId = resolveSelection(models)
                        val selectedModel = models.find { it.id == selectedModelId }
                        val available = ReasoningResolver.availableModes(
                            supported = selectedModel?.supportedReasoningEfforts.orEmpty(),
                            effortAccess = selectedModel?.reasoningEffortAccess.orEmpty(),
                            isEligible = isSubscriptionEligible,
                        )
                        fetched.copy(
                            selectedModelId = selectedModelId,
                            selectedModelShortName = selectedModel?.shortName,
                            selectedReasoningMode = validateAndPersistReasoningMode(fetched.selectedReasoningMode, available),
                            availableReasoningModes = available,
                            newChatDefault = null,
                        )
                    }
                    logcat {
                        "Duck.ai Model Manager: fetched ${models.size} models, tier=$userTier, selected=${_modelState.value.selectedModelId}"
                    }
                }
            } catch (e: Exception) {
                logcat { "Duck.ai Model Manager: failed to fetch models: ${e.message}" }
                if (resetsModelForNewChats()) stateMutex.withLock { selectHardcodedDefaultWhileModelsMissing() }
            }
        }
    }

    /**
     * Applies the latest defaults to the cached models. Callers hold [stateMutex].
     */
    private fun ModelState.withNewChatDefault(): ModelState {
        if (userDefaultModel != null && !userDefaultModelRepository.isUserDefaultEnabled()) userDefaultModel = null
        val newChatDefault = NewChatDefaults.preferred(
            userTier,
            remoteNewChatDefault,
            overLimitDefault,
            endpointModels,
            nowMillis = currentTimeProvider.currentTimeMillis(),
            userDefault = userDefaultModel,
        )
        val selectedModelId = NewChatDefaults.modelId(userTier, newChatDefault, endpointModels)
        val selectedModel = endpointModels.find { it.id == selectedModelId }
        val available = ReasoningResolver.availableModes(
            supported = selectedModel?.supportedReasoningEfforts.orEmpty(),
            effortAccess = selectedModel?.reasoningEffortAccess.orEmpty(),
            isEligible = isSubscriptionEligible,
        )
        return copy(
            selectedModelId = selectedModelId,
            selectedModelShortName = selectedModel?.shortName,
            selectedReasoningMode = NewChatDefaults.reasoningMode(newChatDefault, selectedModelId, available),
            availableReasoningModes = available,
            newChatDefault = newChatDefault,
        )
    }

    /** Before a fetch the hardcoded default stands, and with the flag off there is no new-chat default to move. */
    private suspend fun applyOverLimitDefault(overLimit: OverLimitNewChatDefault?) = stateMutex.withLock {
        overLimitDefault = overLimit
        if (endpointModels.isEmpty() || _modelState.value.newChatDefault == null) return@withLock
        _modelState.value = _modelState.value.withNewChatDefault()
        logcat { "Duck.ai Model Manager: over-limit default ${overLimit?.modelId}, selected=${_modelState.value.selectedModelId}" }
    }

    private suspend fun applyUserDefault(userDefault: UserDefaultModel?) = stateMutex.withLock {
        val state = _modelState.value
        if (!userDefaultModelRepository.isUserDefaultEnabled()) {
            userDefaultModel = null
            val current = state.newChatDefault
            if (current != null && current.userDefaultOutcome != UserDefaultOutcome.NONE) {
                _modelState.value = state.copy(newChatDefault = current.copy(userDefaultOutcome = UserDefaultOutcome.NONE))
            }
            return@withLock
        }
        userDefaultModel = userDefault
        if (endpointModels.isEmpty() || state.newChatDefault == null) return@withLock
        _modelState.value = state.withNewChatDefault()
    }

    /**
     * Without a model list a new chat would go out with no model and the page would choose one, so the
     * tier default is sent unchecked until a fetch succeeds. Callers hold [stateMutex].
     */
    private suspend fun selectHardcodedDefaultWhileModelsMissing() {
        if (_modelState.value.models.isNotEmpty()) return
        val newChatDefault = NewChatDefaults.preferred(resolveUserTier(), remote = null)
        _modelState.value = _modelState.value.copy(
            selectedModelId = newChatDefault.modelId,
            selectedReasoningMode = newChatDefault.mode,
            newChatDefault = newChatDefault,
        )
        logcat { "Duck.ai Model Manager: models not loaded, selected hardcoded default ${newChatDefault.modelId}" }
    }

    private suspend fun fetchModelsResponse(): AIChatModelsResponse {
        val url = DuckAiModelsService.modelsUrl(duckAiHostProvider.getHost())
        val cookies = if (duckAiHostProvider.isCustomHost()) {
            cookiesManager.forMode(BrowserMode.REGULAR)?.getCookie(url)
        } else {
            null
        }
        return modelsService.getModels(url, authorizationHeader(), cookies)
    }

    // The picker sublines (model `label`) are only returned on an authenticated request, so the
    // token rides along only while the updated pickers are the ones consuming it.
    private suspend fun authorizationHeader(): String? {
        if (!duckChatFeature.value.updatedPickers().isEnabled()) return null
        return runCatching {
            subscriptions.getAccessToken()?.takeUnless { it.isBlank() }?.let { "Bearer $it" }
        }.getOrElse {
            logcat { "Duck.ai Model Manager: failed to resolve access token, fetching models unauthenticated: ${it.message}" }
            null
        }
    }

    /**
     * Only an explicit pick in [selectModel] or [selectProvider] is persisted. The default is
     * re-derived from every response so that reordering the list, or promoting a new default,
     * reaches users who never picked a model. A persisted model pick that is gone from the response,
     * or no longer accessible, is dropped rather than replaced, so we don't turn our fallback into a
     * pick the user never made. A persisted provider is kept even when nothing matches, since the
     * provider may return to the response later.
     */
    private suspend fun resolveSelection(models: List<AIChatModel>): String? {
        val persistedId = dataStore.getSelectedModel()?.id
        if (persistedId != null) {
            val persisted = models.find { it.id == persistedId }
            if (persisted != null && persisted.isAccessible) return persistedId
            dataStore.setSelectedModel(null)
        }

        val persistedProvider = ModelProvider.fromNameOrNull(dataStore.getSelectedProvider())
        if (persistedProvider != null) {
            val model = models.firstOrNull { it.provider == persistedProvider && it.isAccessible }
            if (model != null) return model.id
        }

        return models.firstOrNull { it.isAccessible }?.id
    }

    /**
     * With this on, the global selection is always the tier default and picks live on the tab, so nothing
     * here reads or writes the persisted pick: turning it off brings back the user's last-used model.
     */
    private suspend fun resetsModelForNewChats(): Boolean = withContext(dispatcherProvider.io()) {
        duckChatFeature.value.resetModelForNewChats().isEnabled()
    }

    private companion object {
        const val MAX_LABEL_LENGTH = 40
        const val PINNED_DEFAULT_MODEL_ID = "gpt-5.4-mini"
    }

    override suspend fun selectModel(model: AIChatModel) {
        if (resetsModelForNewChats()) return
        withContext(dispatcherProvider.io()) {
            stateMutex.withLock {
                selectionRevision++
                persistModelSelection(model)
            }
        }
    }

    private suspend fun persistModelSelection(model: AIChatModel) {
        dataStore.setSelectedModel(SelectedModel(model.id, model.shortName))
        dataStore.setSelectedProvider(null)
        publishSelection(modelId = model.id, model = model)
        logcat { "Duck.ai Model Manager: selected model ${model.id} (${model.shortName})" }
    }

    override suspend fun ensureImageCapableModelSelected(): Boolean = withContext(dispatcherProvider.io()) {
        val revision = stateMutex.withLock { selectionRevision }
        if (_modelState.value.models.isEmpty()) fetchModels()
        if (resetsModelForNewChats()) {
            return@withContext _modelState.value.models.any { it.isAccessible && it.supportsTool(Tool.IMAGE_GENERATION) }
        }
        stateMutex.withLock {
            if (selectionRevision != revision) return@withLock false
            val state = _modelState.value
            val selectedModel = state.models.firstOrNull { it.id == state.selectedModelId }
            if (selectedModel?.supportsTool(Tool.IMAGE_GENERATION) == true) return@withLock true
            val imageCapableModel = state.models.firstOrNull {
                it.isAccessible && it.supportsTool(Tool.IMAGE_GENERATION)
            } ?: return@withLock false
            persistModelSelection(imageCapableModel)
            true
        }
    }

    override suspend fun selectProvider(provider: ModelProvider) {
        if (resetsModelForNewChats()) return
        withContext(dispatcherProvider.io()) {
            stateMutex.withLock {
                selectionRevision++
                dataStore.setSelectedModel(null)
                dataStore.setSelectedProvider(provider.name)

                val models = _modelState.value.models
                val selectedModelId = resolveSelection(models)
                publishSelection(modelId = selectedModelId, model = models.find { it.id == selectedModelId })
                logcat { "Duck.ai Model Manager: selected provider ${provider.name}, resolved model $selectedModelId" }
            }
        }
    }

    /**
     * Publishes [modelId] as the current selection, re-deriving the reasoning modes [model] supports and
     * keeping the persisted mode only while it stays available. Callers hold [stateMutex].
     */
    private suspend fun publishSelection(
        modelId: String?,
        model: AIChatModel?,
    ) {
        val available = ReasoningResolver.availableModes(
            supported = model?.supportedReasoningEfforts.orEmpty(),
            effortAccess = model?.reasoningEffortAccess.orEmpty(),
            isEligible = _modelState.value.isSubscriptionEligible,
        )
        val nextReasoningMode = validateAndPersistReasoningMode(_modelState.value.selectedReasoningMode, available)
        _modelState.value = _modelState.value.copy(
            selectedModelId = modelId,
            selectedModelShortName = model?.shortName,
            selectedReasoningMode = nextReasoningMode,
            availableReasoningModes = available,
        )
    }

    override suspend fun selectReasoningMode(mode: ReasoningMode) {
        if (resetsModelForNewChats()) return
        withContext(dispatcherProvider.io()) {
            stateMutex.withLock {
                val match = _modelState.value.availableReasoningModes.firstOrNull { it.mode == mode }
                if (match == null || !match.isAccessible) return@withLock
                dataStore.setSelectedReasoningMode(mode.rawValue)
                _modelState.value = _modelState.value.copy(selectedReasoningMode = mode)
                logcat { "Duck.ai Model Manager: selected reasoning mode ${mode.rawValue}" }
            }
        }
    }

    override suspend fun setChatScopedReasoningMode(mode: ReasoningMode?) {
        stateMutex.withLock {
            _modelState.value = _modelState.value.copy(chatScopedReasoningMode = mode)
        }
    }

    override fun getSelectedModelId(): String? = _modelState.value.selectedModelId

    override fun getResolvedReasoningEffort(): String? {
        val state = _modelState.value
        return ReasoningResolver.effortFor(state.selectedReasoningMode, state.availableReasoningModes)?.rawValue
    }

    private suspend fun validateAndPersistReasoningMode(
        persisted: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningMode? {
        val match = available.firstOrNull { it.mode == persisted }
        if (match != null && match.isAccessible) return persisted
        val next = available.firstOrNull { it.isAccessible }?.mode
        if (next != persisted) {
            dataStore.setSelectedReasoningMode(next?.rawValue)
        }
        return next
    }

    private suspend fun resolveUserTier(): UserTier {
        return try {
            if (!subscriptions.getSubscriptionStatus().isActiveOrWaiting()) return UserTier.FREE
            val entitlements = subscriptions.getEntitlements().firstOrNull().orEmpty()
            val duckAiTier = entitlements.firstOrNull { it.product == Product.DuckAiPlus.value }?.name
            UserTier.from(duckAiTier) ?: UserTier.FREE
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
            files = tierLimits.files?.let { remote ->
                FileLimits(
                    maxPerConversation = remote.maxPerConversation ?: FileLimits.DEFAULT_FILE_MAX_PER_CONVERSATION,
                    maxFileSizeBytes = remote.maxFileSizeMB?.let { it.toLong() * 1024 * 1024 }
                        ?: FileLimits.DEFAULT_FILE_MAX_SIZE_BYTES,
                    maxTotalFileSizeBytes = remote.maxTotalFileSizeBytes
                        ?: remote.maxFileSizeMB?.let { it.toLong() * 1024 * 1024 }
                        ?: FileLimits.DEFAULT_FILE_MAX_SIZE_BYTES,
                    maxPagesPerFile = remote.maxPagesPerFile ?: FileLimits.DEFAULT_FILE_MAX_PAGES,
                )
            } ?: FileLimits(),
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

    /** Flags labels added after this version shipped, so we notice copy we cannot render. Models are
     * re-fetched on every picker attach; the pixel's daily tag keeps that down to one report per label
     * per day. */
    private fun reportUnknownLabels(remote: List<RemoteAIChatModel>) {
        remote.asSequence()
            .mapNotNull { it.label }
            .filter { ModelLabel.from(it) == ModelLabel.UNKNOWN }
            .map { it.sanitisedLabel() }
            .distinct()
            .forEach { duckChatPixels.value.fireUnknownModelLabel(it) }
    }

    // The label is a backend-authored id, so keep the pixel to that shape rather than passing
    // whatever arrives through verbatim.
    private fun String.sanitisedLabel(): String = uppercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(MAX_LABEL_LENGTH)
        .ifEmpty { "UNPARSEABLE" }

    /** Labelled models lead the list, as the backend marks them as the ones to recommend. */
    private suspend fun List<AIChatModel>.sortLabelledFirst(): List<AIChatModel> {
        if (!duckChatFeature.value.updatedPickers().isEnabled()) return this
        return sortedBy { if (it.label != null) 0 else 1 }
    }

    private fun resolveModel(
        remote: RemoteAIChatModel,
        userTier: UserTier,
    ): AIChatModel {
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
            supportedFileTypes = remote.supportedFileTypes.orEmpty(),
            supportedReasoningEfforts = remote.supportedReasoningEffort.orEmpty().mapNotNull(ReasoningEffort::from),
            reasoningEffortAccess = remote.reasoningEffortAccess.orEmpty().mapNotNull { entry ->
                val effort = ReasoningEffort.from(entry.id) ?: return@mapNotNull null
                val tiers = entry.accessTier.orEmpty()
                val accessible = if (tiers.isEmpty()) entry.entityHasAccess else tiers.contains(userTier.rawValue)
                ReasoningEffortAccess(effort = effort, accessTier = tiers, isAccessible = accessible)
            },
            supportedTools = remote.supportedTools.orEmpty().mapNotNull(Tool::from),
            label = ModelLabel.from(remote.label),
        )
    }
}

private fun com.duckduckgo.subscriptions.api.SubscriptionStatus.isActiveOrWaiting(): Boolean {
    return this == com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD ||
        this == com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
}
