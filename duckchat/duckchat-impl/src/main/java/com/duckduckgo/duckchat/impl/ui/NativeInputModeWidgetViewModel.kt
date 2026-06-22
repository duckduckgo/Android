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

package com.duckduckgo.duckchat.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteFactory
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InteractionLock
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.feature.maxUrlSuggestions
import com.duckduckgo.duckchat.impl.helper.PendingNativeFile
import com.duckduckgo.duckchat.impl.helper.PendingNativeImage
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ReasoningResolver
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatTabSuggestions(
    val chatHistory: List<ChatSuggestion>,
    val urlSuggestions: AutoCompleteResult,
)

@ContributesViewModel(ViewScope::class)
class NativeInputModeWidgetViewModel @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    duckAiFeatureState: DuckAiFeatureState,
    subscriptions: Subscriptions,
    private val pendingNativePromptStore: PendingNativePromptStore,
    private val chatSuggestionsReader: ChatSuggestionsReader,
    private val nativeInputPlugins: ActivePluginPoint<NativeInputPlugin>,
    autoCompleteFactory: AutoCompleteFactory,
    private val autoCompleteSettings: AutoCompleteSettings,
    private val duckAiChatHistoryFeature: DuckAiChatHistoryFeature,
    private val dispatchers: DispatcherProvider,
    private val inputScreenConfigResolver: InputScreenConfigResolver,
    private val pixel: Pixel,
    private val duckChatPixels: DuckChatPixels,
    private val nativeInputStatePublisher: NativeInputStatePublisher,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val modelManager: DuckAiModelManager,
    private val duckAiChatStore: DuckAiChatStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ViewModel() {

    private val autoComplete: AutoComplete = autoCompleteFactory.create(
        AutoComplete.Config(showInstalledApps = inputScreenConfigResolver.shouldShowInstalledApps()),
    )

    // Captures the URL list returned from the most recent fetchChatTabSuggestions so that
    // fireChatUrlSuggestionPixel can credit the right suggestions when the user clicks one.
    @Volatile
    private var lastChatUrlSuggestions: List<AutoCompleteSuggestion> = emptyList()

    // Whether the most recent empty-query chat-history fetch returned any chats. Read synchronously
    // (via [hadRecentChats]) by the widget to decide whether to show the suggestions cover when the
    // Duck.ai tab is selected with empty input. Covering only when content will actually appear keeps
    // the morphing logo from being briefly covered then revealed (a flash) when there are no chats.
    // Stale until the first empty-query fetch completes, so the first such transition will not cover.
    @Volatile
    private var lastEmptyQueryHadChats: Boolean = false

    /** @see lastEmptyQueryHadChats */
    fun hadRecentChats(): Boolean = lastEmptyQueryHadChats

    private val _plugins = MutableStateFlow<List<NativeInputPlugin>>(emptyList())
    val plugins: StateFlow<List<NativeInputPlugin>> = _plugins.asStateFlow()

    private val _modelPickerEnabled = MutableStateFlow(true)
    val modelPickerEnabled: StateFlow<Boolean> = _modelPickerEnabled.asStateFlow()

    private val _isHistoryAvailable = MutableStateFlow(false)
    val isHistoryAvailable: StateFlow<Boolean> = _isHistoryAvailable.asStateFlow()

    private val currentChat = MutableStateFlow<DuckAiChat?>(null)
    private var currentChatJob: Job? = null

    // Defensive buffer for the (rare) case where setActiveChatId fires before configure has set
    // activeTabId. Replayed inside configure / configureContextual when activeTabId becomes known.
    private var pendingChatId: String? = null
    private var hasPendingChatId = false
    private var pendingInteractionLock: InteractionLock? = null
    private var pendingDuckAiFireButtonHighlighted: Boolean? = null

    init {
        viewModelScope.launch {
            _plugins.value = nativeInputPlugins.getPlugins().toList()
        }
        viewModelScope.launch {
            _isHistoryAvailable.value = duckChatInternal.isChatHistoryAvailable()
        }
        viewModelScope.launch {
            // FE recovery "Switch Model": enter the model-change mode, but only when the event
            // targets this widget's tab. The event carries a tabId, so other tabs' VMs ignore it.
            duckChatInternal.showModelPickerEvents.collect { eventTabId ->
                if (eventTabId == activeTabId.value) {
                    nativeInputStatePublisher.update(eventTabId) { it.copy(modelChangeMode = true) }
                }
            }
        }
    }

    /**
     * Events asking the widget to open the model picker (e.g. for the FE recovery flow) for the related tabId.
     */
    val showModelPickerEvents: Flow<Unit> = duckChatInternal.showModelPickerEvents
        .filter { it == activeTabId.value }
        .map { }

    fun setModelPickerEnabled(enabled: Boolean) {
        _modelPickerEnabled.value = enabled
    }

    // currentChat can briefly hold the previous chat while a getChatById lookup is in flight.
    // Returns it only when it matches the active tab's published chatId.
    private fun validChat(): DuckAiChat? {
        val activeChatId = activeTabId.value?.let { nativeInputStateProvider.stateForTab(it).value.chatId }
        return currentChat.value?.takeIf { it.chatId == activeChatId }
    }

    fun getSelectedModelId(): String? {
        // Existing chat with its model still in the list: send the chat's stored model.
        val chat = validChat()
        if (chat != null && ReasoningResolver.forChat(chat, modelManager.modelState.value) != null) {
            return chat.model
        }
        // Existing chat whose model is no longer in the list: fall through to global so submission
        // carries (global model, global effort). New chat with picker off: nothing to send.
        if (chat == null && !_modelPickerEnabled.value) return null
        return modelManager.getSelectedModelId()
    }

    fun getResolvedReasoningEffort(): String? {
        val chat = validChat() ?: return modelManager.getResolvedReasoningEffort()
        val resolution = ReasoningResolver.forChat(chat, modelManager.modelState.value)
            ?: return modelManager.getResolvedReasoningEffort()
        val mode = ReasoningResolver.resolveMode(persisted = resolution.mode, available = resolution.available)
        return ReasoningResolver.effortFor(mode, resolution.available)?.rawValue
    }

    fun getSelectedTool(): String? {
        val tabId = activeTabId.value ?: return null
        return nativeInputStateProvider.stateForTab(tabId).value.selectedTool
    }

    /**
     * Fires the unified prompt-submitted pixel plus, when a tool is selected, the matching per-tool
     * submitted pixel. Called exactly once per Duck.ai (AI-chat) submission by the widget. Attachment
     * presence is passed in because the attachment lists live in the widget's AttachmentView, not here.
     */
    fun fireSubmissionPixels(
        hasText: Boolean,
        hasImageAttachment: Boolean,
        hasFileAttachment: Boolean,
    ) {
        val tool = getSelectedTool()?.let { Tool.from(it) }
        val selectedToolParam = when (tool) {
            Tool.IMAGE_GENERATION -> "image_generation"
            Tool.WEB_SEARCH -> "web_search"
            null -> "none"
        }
        duckChatPixels.firePromptSubmitted(
            selectedTool = selectedToolParam,
            modelId = getSelectedModelId(),
            reasoningEffort = getResolvedReasoningEffort(),
            hasImageAttachment = hasImageAttachment,
            hasFileAttachment = hasFileAttachment,
            hasText = hasText,
        )
        when (tool) {
            Tool.IMAGE_GENERATION -> duckChatPixels.fireImageGenerationSubmitted()
            Tool.WEB_SEARCH -> duckChatPixels.fireWebSearchSubmitted()
            null -> {}
        }
    }

    fun fireVoiceTapped() = duckChatPixels.fireVoiceTapped()

    fun fireStopGenerationTapped() = duckChatPixels.fireStopGenerationTapped()

    private data class WidgetConfig(
        val inputContext: NativeInputState.InputContext = NativeInputState.InputContext.BROWSER,
        val inputPosition: NativeInputState.InputPosition = NativeInputState.InputPosition.TOP,
        // null = follow the context-derived default; non-null = user (or widget) has set it explicitly
        // via setToggleSelection. Reset to null on every configure(), then driven by the widget's
        // TabLayout listener.
        val toggleSelection: NativeInputState.ToggleSelection? = null,
    )

    private val widgetConfig = MutableStateFlow(WidgetConfig())

    private val activeTabId = MutableStateFlow<String?>(null)

    private val baseState: Flow<NativeInputState> = combine(
        duckAiFeatureState.showSettings,
        duckChatInternal.observeEnableDuckChatUserSetting(),
        duckChatInternal.observeInputScreenUserSettingEnabled(),
        widgetConfig,
        activeTabId.filterNotNull(),
    ) { isFeatureEnabled, isUserEnabled, isInputScreenUserSettingEnabled, config, _ ->
        NativeInputState(
            inputMode = getInputMode(isFeatureEnabled && isUserEnabled, isInputScreenUserSettingEnabled),
            inputContext = config.inputContext,
            inputPosition = config.inputPosition,
            toggleSelection = config.toggleSelection ?: NativeInputState.defaultToggleFor(config.inputContext),
        )
    }

    // chatId lives in the per-tab provider state (written by applyChatId), not in baseState. Fold it
    // back in here so widget UI that depends on it (e.g. the chat input hint) sees the real value
    // rather than the baseState default of null. flatMapLatest re-targets on tab switch.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeChatId: Flow<String?> = activeTabId.filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId) }
        .map { it.chatId }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val submitEnabled: Flow<Boolean> = activeTabId.filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId) }
        .map { it.submitEnabled }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val modelChangeMode: Flow<Boolean> = activeTabId.filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId) }
        .map { it.modelChangeMode }
        .distinctUntilChanged()

    // interactionLock / duckAiFireButtonHighlighted live in the per-tab provider state (written by
    // setInteractionLock / setDuckAiFireButtonHighlighted), not in baseState. Fold them back into `state` here
    // — same as chatId above — so the widget's applyState sees the published values rather than the defaults.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val interactionLock: Flow<InteractionLock> = activeTabId.filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId) }
        .map { it.interactionLock }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val duckAiFireButtonHighlighted: Flow<Boolean> = activeTabId.filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId) }
        .map { it.duckAiFireButtonHighlighted }
        .distinctUntilChanged()

    val state: SharedFlow<NativeInputState> = combine(
        baseState,
        duckChatInternal.chatState,
        activeChatId,
        interactionLock,
        duckAiFireButtonHighlighted,
    ) { state, chatState, chatId, lock, fireHighlighted ->
        state.copy(
            isChatStreaming = chatState == ChatState.STREAMING || chatState == ChatState.LOADING,
            chatId = chatId,
            interactionLock = lock,
            duckAiFireButtonHighlighted = fireHighlighted,
        )
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    init {
        // Publish only the widget-owned fields via update, leaving plugin-owned contributions (added
        // later by typed host methods) untouched across widget emissions. This keeps the widget VM
        // the single writer for its fields without clobbering anything the publisher.update path
        // wrote on behalf of a plugin.
        //
        // Pair each state with the active tabId via combine so the publish target is captured at
        // emission time rather than read separately from activeTabId.value (which could shift
        // between emission and read on a fast tab switch).
        viewModelScope.launch {
            combine(state, activeTabId.filterNotNull()) { snapshot, tabId -> tabId to snapshot }
                .collect { (tabId, snapshot) ->
                    nativeInputStatePublisher.update(tabId) { current ->
                        current.copy(
                            inputMode = snapshot.inputMode,
                            inputContext = snapshot.inputContext,
                            inputPosition = snapshot.inputPosition,
                            toggleSelection = snapshot.toggleSelection,
                            isChatStreaming = snapshot.isChatStreaming,
                        )
                    }
                }
        }
    }

    // Kept for the widget's observeChatState() which handles HIDE/SHOW/READY root-visibility transitions.
    val chatState: Flow<ChatState> = duckChatInternal.chatState

    val isPaidTier: Flow<Boolean> = subscriptions.getEntitlementStatus()
        .map { entitlements -> entitlements.any { it == Product.DuckAiPlus } }

    val chatSuggestionsUserEnabled: Flow<Boolean> = duckChatInternal.observeChatSuggestionsUserSettingEnabled()

    val defaultTogglePosition: Flow<DefaultTogglePosition> = duckChatInternal.observeDefaultTogglePosition()

    val lastUsedTogglePosition: Flow<String?> = duckChatInternal.observeLastUsedTogglePosition()

    suspend fun saveLastUsedTogglePosition(position: String) {
        duckChatInternal.saveLastUsedTogglePosition(position)
    }

    fun openNewChat() {
        duckChatInternal.openNewDuckChatSession()
    }

    fun setDuckAiMode(isDuckAiMode: Boolean) {
        val context = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
        widgetConfig.update { it.copy(inputContext = context) }
    }

    fun setWidgetPosition(isBottom: Boolean) {
        val position = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
        widgetConfig.update { it.copy(inputPosition = position) }
    }

    fun setToggleSelection(selection: NativeInputState.ToggleSelection) {
        widgetConfig.update { it.copy(toggleSelection = selection) }
    }

    fun setSelectedTool(tool: String?) {
        val tabId = activeTabId.value ?: return
        nativeInputStatePublisher.update(tabId) { it.copy(selectedTool = tool) }
    }

    fun setInteractionLock(lock: InteractionLock) {
        val tabId = activeTabId.value
        if (tabId == null) {
            // configure hasn't run yet — buffer until activeTabId is known, replayed in configure.
            pendingInteractionLock = lock
            return
        }
        nativeInputStatePublisher.update(tabId) { it.copy(interactionLock = lock) }
    }

    fun setDuckAiFireButtonHighlighted(highlighted: Boolean) {
        val tabId = activeTabId.value
        if (tabId == null) {
            pendingDuckAiFireButtonHighlighted = highlighted
            return
        }
        nativeInputStatePublisher.update(tabId) { it.copy(duckAiFireButtonHighlighted = highlighted) }
    }

    /**
     * Called when a prompt is submitted
     * */
    fun onPromptSubmitted() {
        // A prompt submitted while still in the recovery window means the user sent a prompt after
        // recovering the chat's model — report it before the window is cleared below.
        val tabId = activeTabId.value
        if (tabId != null && nativeInputStateProvider.stateForTab(tabId).value.modelChangeMode) {
            duckChatPixels.fireSubmitChangeModelPromptSent()
        }
        // Ends the FE recovery model-change window for the active tab.
        endModelChangeMode()
    }

    /**
     * Called when the recovery model picker is dismissed without picking a model.
     */
    fun exitModelChangeMode() {
        endModelChangeMode()
    }

    private fun endModelChangeMode() {
        val tabId = activeTabId.value ?: return
        nativeInputStatePublisher.update(tabId) { it.copy(modelChangeMode = false) }
    }

    fun setActiveChatId(chatId: String?) {
        val tabId = activeTabId.value
        if (tabId == null) {
            // configure hasn't run yet — buffer until activeTabId is known, replayed in configure.
            pendingChatId = chatId
            hasPendingChatId = true
            return
        }
        applyChatId(tabId, chatId)
    }

    private fun applyChatId(tabId: String, chatId: String?) {
        nativeInputStatePublisher.update(tabId) { current ->
            // Reset submitEnabled to true if we changed chats.
            // Always end the model-change window since it's per-visit.
            val submitEnabled = if (current.chatId != chatId) true else current.submitEnabled
            current.copy(chatId = chatId, submitEnabled = submitEnabled, modelChangeMode = false)
        }
        currentChatJob?.cancel()
        currentChat.value = null
        currentChatJob = viewModelScope.launch {
            modelManager.setChatScopedReasoningMode(null)
            if (chatId != null) currentChat.value = duckAiChatStore.getChatById(chatId)
        }
    }

    fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean) {
        activeTabId.value = tabId
        val context = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
        val position = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
        widgetConfig.value = WidgetConfig(inputContext = context, inputPosition = position)
        replayPendingState(tabId)
    }

    private fun replayPendingState(tabId: String) {
        if (hasPendingChatId) {
            val pending = pendingChatId
            pendingChatId = null
            hasPendingChatId = false
            applyChatId(tabId, pending)
        }
        pendingInteractionLock?.let { lock ->
            pendingInteractionLock = null
            nativeInputStatePublisher.update(tabId) { it.copy(interactionLock = lock) }
        }
        pendingDuckAiFireButtonHighlighted?.let { highlighted ->
            pendingDuckAiFireButtonHighlighted = null
            nativeInputStatePublisher.update(tabId) { it.copy(duckAiFireButtonHighlighted = highlighted) }
        }
    }

    fun storePendingPrompt(
        query: String,
        modelId: String?,
        reasoningEffort: String?,
        selectedTool: String? = null,
        images: List<PendingNativeImage> = emptyList(),
        files: List<PendingNativeFile> = emptyList(),
    ) {
        pendingNativePromptStore.store(query, modelId, reasoningEffort, selectedTool, images, files)
    }

    fun configureContextual(tabId: String) {
        activeTabId.value = tabId
        widgetConfig.update { it.copy(inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL) }
        replayPendingState(tabId)
    }

    fun cancelChatSuggestions() {
        chatSuggestionsReader.tearDown()
        lastChatUrlSuggestions = emptyList()
    }

    suspend fun fetchChatTabSuggestions(
        query: String,
        chatSuggestionsEnabled: Boolean,
    ): ChatTabSuggestions = coroutineScope {
        val chatHistoryDeferred = async {
            if (chatSuggestionsEnabled) {
                runCatching { chatSuggestionsReader.fetchSuggestions(query) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val urlSuggestionsDeferred = async(dispatchers.io()) {
            if (autoCompleteSettings.autoCompleteSuggestionsEnabled && query.isNotEmpty()) {
                runCatching {
                    val raw = autoComplete.autoComplete(query).firstOrNull()
                        ?: return@runCatching AutoCompleteResult(query, emptyList())
                    raw.copy(
                        suggestions = raw.suggestions.filter {
                            it is AutoCompleteBookmarkSuggestion ||
                                it is AutoCompleteSwitchToTabSuggestion ||
                                it is AutoCompleteHistorySuggestion ||
                                (it is AutoCompleteSearchSuggestion && it.isUrl)
                        }.take(duckAiChatHistoryFeature.maxUrlSuggestions()),
                    )
                }.getOrDefault(AutoCompleteResult(query, emptyList()))
            } else {
                AutoCompleteResult(query, emptyList())
            }
        }
        val chatHistory = chatHistoryDeferred.await()
        if (query.isEmpty()) {
            // Remember whether an empty query yields chats so the cover decision can predict it next time.
            lastEmptyQueryHadChats = chatHistory.isNotEmpty()
        }
        val result = ChatTabSuggestions(
            chatHistory = chatHistory,
            urlSuggestions = urlSuggestionsDeferred.await(),
        )
        lastChatUrlSuggestions = result.urlSuggestions.suggestions
        result
    }

    fun fireChatUrlSuggestionPixel(suggestion: AutoCompleteSuggestion) {
        val suggestionsShown = lastChatUrlSuggestions
        // Use appCoroutineScope so the pixel fire survives the widget detach
        appCoroutineScope.launch(dispatchers.io()) {
            autoComplete.fireAutocompletePixel(suggestionsShown, suggestion, experimentalInputScreen = true, duckAiSurface = true)
        }
    }

    fun fireChatHistorySelectedPixel(pinned: Boolean) {
        if (pinned) {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_DAILY, type = Daily())
        } else {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_COUNT)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_DAILY, type = Daily())
        }
        // The autocomplete-family pixel sits alongside the RECENT_CHAT_SELECTED metrics above: those
        // measure recent-chat re-entry, this one credits the Duck.ai-tab autocomplete surface.
        duckChatPixels.fireDuckAiChatHistorySuggestionClicked()
    }

    fun fireDuckAiSearchForQuerySubmittedPixel() {
        duckChatPixels.fireDuckAiSearchDuckDuckGoSuggestionClicked()
    }

    fun buildChatSuggestionUrl(suggestion: ChatSuggestion): String =
        duckChatInternal.buildChatUrl(suggestion.chatId)

    private fun getInputMode(
        isEnabled: Boolean,
        isInputScreenUserSettingEnabled: Boolean,
    ): NativeInputState.InputMode =
        if (isEnabled && isInputScreenUserSettingEnabled) {
            NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        } else {
            NativeInputState.InputMode.SEARCH_ONLY
        }
}
