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

package com.duckduckgo.duckchat.impl.contextual

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.NativeAction
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.history.ChatHistoryItem
import com.duckduckgo.duckchat.impl.history.ChatHistoryRepository
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

/**
 * Drives the redesign-ON contextual sheet, which only ever shows the chat-in-progress WebView (the
 * INPUT/entry stage lives in [DuckChatContextualEntryDialog]). Compared to [DuckChatContextualViewModel]
 * this drops all SheetMode/QuickAction/suggestions/keyboard-driven-sheet-state logic; the sheet is
 * always in the WebView state and carries only the follow-up composer alongside the chat.
 */
@ContributesViewModel(FragmentScope::class)
class DuckChatContextualWebViewViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
    private val duckChatInternal: DuckChatInternal,
    private val duckChatJSHelper: DuckChatJSHelper,
    private val contextualDataStore: DuckChatContextualDataStore,
    private val sessionTimeoutProvider: DuckChatContextualSessionTimeoutProvider,
    private val timeProvider: DuckChatContextualTimeProvider,
    private val duckChatPixels: DuckChatPixels,
    private val duckChatFeature: DuckChatFeature,
    private val modelManager: DuckAiModelManager,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val contextualEntryPromptStore: ContextualEntryPromptStore,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private val _subscriptionEventDataChannel = Channel<SubscriptionEventData>(capacity = Channel.BUFFERED)
    val subscriptionEventDataFlow = _subscriptionEventDataChannel.receiveAsFlow()

    private var fullModeUrl: String = ""

    private data class PageContextState(
        // The current page reported by the browser — what a manual attach grabs.
        val currentPage: String = "",
        // The frozen snapshot actually submitted for the current attachment, so passive navigation
        // doesn't change what an existing attachment sends.
        val attachedPage: String = "",
    )

    private var pageContextState = PageContextState()

    var currentPageContext: String
        get() = pageContextState.currentPage
        set(value) {
            pageContextState = pageContextState.copy(currentPage = value)
        }

    // Chat id currently shown in the contextual webview, derived from the URL query param.
    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId.asStateFlow()

    // A prompt handed over from the contextual entry dialog, submitted once the chat web app signals it
    // is ready (onWebAppReady). Held between opening the sheet and that ready signal.
    private var pendingEntryPrompt: NativeInputPrompt? = null

    private var hidingSheetForNewChat = false

    sealed class Command {
        data class LoadUrl(val url: String) : Command()
        data class OpenFullscreenMode(val url: String) : Command()
        data class ChangeSheetState(
            val newState: Int,
            val prefillNativeInput: String? = null,
            val hideKeyboard: Boolean = false,
        ) : Command()
        data object RequestPageContext : Command()
        data object ShowFireConfirmation : Command()
        data class ShowChatsPopup(val recentChats: List<ChatHistoryItem>) : Command()
        data class ShowNewChatEntryDialog(val tabId: String) : Command()
        data class OpenChatUrl(
            val url: String,
            val sourceTabId: String,
        ) : Command()
        data object LaunchChatHistory : Command()
        data object FocusInput : Command()
        data class OpenSearchInNewTab(val query: String) : Command()
        data class ApplyContextualReopened(val tabId: String) : Command()
        data class ApplyContextualClosed(val tabId: String) : Command()
    }

    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.update {
                it.copy(isFireButtonEnabled = duckChatFeature.contextualFireButton().isEnabled())
            }
            observeRecentChats()
        }
    }

    data class ViewState(
        val showFullscreen: Boolean = true,
        val showContext: Boolean = false,
        val contextUrl: String = "",
        val contextTitle: String = "",
        val tabId: String = "",
        val isFireButtonEnabled: Boolean = false,
        val recentChats: List<ChatHistoryItem> = emptyList(),
    )

    private suspend fun isStoredChatMissingFromHistory(url: String?): Boolean {
        val chatId = extractChatId(url) ?: return false
        val chats = chatHistoryRepository.observeChats().firstOrNull() ?: return false
        return chats.none { it.chatId == chatId }
    }

    private fun observeRecentChats() {
        combine(chatHistoryRepository.observeChats(), _chatId) { chats, currentChatId ->
            chats
                .asSequence()
                .filterNot { it.chatId == currentChatId }
                .sortedByDescending { it.lastEditMillis }
                .take(MAX_RECENT_CHATS)
                .toList()
        }
            .flowOn(dispatchers.io())
            .onEach { recent -> _viewState.update { it.copy(recentChats = recent) } }
            .launchIn(viewModelScope)
    }

    fun onSheetOpened(tabId: String) {
        _viewState.update { it.copy(tabId = tabId) }
        viewModelScope.launch(dispatchers.io()) {
            val pendingEntry = contextualEntryPromptStore.consume(tabId)
            if (pendingEntry != null) {
                // Composed in the entry dialog: open straight into the chat and auto-submit. Page context
                // came over with the prompt, so there's no need to request it again here.
                startChatFromEntryPrompt(tabId, pendingEntry)
                return@launch
            }
            // No fresh prompt for this open — drop any leftover from an aborted hand-off (sheet dismissed
            // before onWebAppReady) so this load's onWebAppReady doesn't auto-submit a stale prompt.
            pendingEntryPrompt = null
            logcat { "Duck.ai: onSheetOpened for tab=$tabId" }
            withContext(dispatchers.main()) {
                commandChannel.trySend(Command.RequestPageContext)
            }

            val existingChatUrl = contextualDataStore.getTabChatUrl(tabId)
            val shouldReuseUrl = !existingChatUrl.isNullOrBlank() &&
                shouldReuseStoredChatUrl(tabId) &&
                !isStoredChatMissingFromHistory(existingChatUrl)
            if (shouldReuseUrl) {
                logcat { "Duck.ai: tab=$tabId has an existing url and don't need to restart the session" }
                loadWebViewUrl(tabId, existingChatUrl!!)
            } else {
                logcat { "Duck.ai: tab=$tabId session expired or absent, starting a new chat" }
                loadFreshChat(tabId)
            }
        }
        duckChatPixels.reportContextualSheetOpened()
    }

    fun onSheetReopened() {
        logcat { "Duck.ai: onSheetReopened" }
        commandChannel.trySend(Command.ApplyContextualReopened(_viewState.value.tabId))

        viewModelScope.launch(dispatchers.io()) {
            val tabId = _viewState.value.tabId
            val pendingEntry = contextualEntryPromptStore.consume(tabId)
            if (pendingEntry != null) {
                // Composed in the entry dialog while the (persisted) sheet was hidden: reload into a fresh
                // chat and auto-submit. The reload makes the web app re-request its hand-off data, so
                // onWebAppReady fires again — the same delivery path as the first open.
                startChatFromEntryPrompt(tabId, pendingEntry)
                return@launch
            }
            // No fresh prompt for this reopen — drop any leftover from an aborted hand-off (sheet dismissed
            // before onWebAppReady) so this load's onWebAppReady doesn't auto-submit a stale prompt.
            pendingEntryPrompt = null
            withContext(dispatchers.main()) {
                logcat { "Duck.ai: requesting page context after sheet reopened" }
                commandChannel.trySend(Command.RequestPageContext)
            }
            reopenWebViewState(tabId)
        }
        duckChatPixels.reportContextualSheetOpened()
    }

    private suspend fun reopenWebViewState(tabId: String) {
        val shouldReuseSession = shouldReuseStoredChatUrl(tabId)
        val existingChatUrl = contextualDataStore.getTabChatUrl(tabId)
        if (!shouldReuseSession || isStoredChatMissingFromHistory(existingChatUrl)) {
            resetToNewChat()
            return
        }
        withContext(dispatchers.main()) {
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
        }
        if (existingChatUrl == null) {
            loadFreshChat(tabId)
        } else {
            withContext(dispatchers.main()) {
                setSheetUrl(existingChatUrl)
                _viewState.update { state ->
                    state.copy(showFullscreen = hasChatId(existingChatUrl))
                }
            }
            duckChatPixels.reportContextualSheetSessionRestored()
        }
    }

    private suspend fun loadFreshChat(tabId: String) {
        val chatUrl = duckChat.getDuckChatUrl("", false, sidebar = true)
        withContext(dispatchers.main()) {
            setSheetUrl(chatUrl)
            _viewState.update {
                it.copy(showFullscreen = hasChatId(chatUrl), tabId = tabId)
            }
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
            commandChannel.trySend(Command.LoadUrl(chatUrl))
        }
    }

    private suspend fun loadWebViewUrl(
        tabId: String,
        existingChatUrl: String,
    ) {
        val hasChatHistory = hasChatId(existingChatUrl)
        withContext(dispatchers.main()) {
            setSheetUrl(existingChatUrl)
            _viewState.update { current ->
                current.copy(showFullscreen = hasChatHistory, tabId = tabId)
            }
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
            commandChannel.trySend(Command.LoadUrl(existingChatUrl))
        }
    }

    private suspend fun startChatFromEntryPrompt(
        tabId: String,
        entry: ContextualEntryPrompt,
    ) {
        pendingEntryPrompt = entry.prompt
        val chatUrl = duckChat.getDuckChatUrl("", false, sidebar = true)
        withContext(dispatchers.main()) {
            setSheetUrl(chatUrl)
            entry.serializedPageContext?.let { attachProvidedPageContext(it) }
            _viewState.update {
                it.copy(showFullscreen = hasChatId(chatUrl), tabId = tabId)
            }
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
            commandChannel.trySend(Command.LoadUrl(chatUrl))
        }
    }

    private fun attachProvidedPageContext(serializedPageContext: String) {
        if (!isContextValid(serializedPageContext)) return
        currentPageContext = serializedPageContext
        pageContextState = pageContextState.copy(attachedPage = serializedPageContext)
        val json = JSONObject(serializedPageContext)
        _viewState.update {
            it.copy(
                showContext = true,
                contextTitle = json.optString("title"),
                contextUrl = json.optString("url"),
            )
        }
    }

    /**
     * Signalled by the fragment when the chat web app has requested its hand-off data (i.e. it is loaded
     * and subscribed). Submitting the entry-dialog prompt here — rather than at page-finished — avoids a
     * race where the prompt event is emitted before the web app can receive it.
     */
    fun onWebAppReady() {
        val entry = pendingEntryPrompt ?: return
        pendingEntryPrompt = null
        onPromptSent(
            prompt = entry.prompt,
            modelId = entry.modelId,
            reasoningEffort = entry.reasoningEffort,
            selectedTool = entry.selectedTool,
            imagesJson = entry.imagesJson,
            filesJson = entry.filesJson,
        )
    }

    fun onPromptSent(
        prompt: String,
        followUpPrefill: String? = null,
        modelId: String? = null,
        reasoningEffort: String? = null,
        selectedTool: String? = null,
        imagesJson: JSONArray? = null,
        filesJson: JSONArray? = null,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            val contextPrompt = generateContextPrompt(prompt, modelId, reasoningEffort, selectedTool, imagesJson, filesJson)
            val prefillText = followUpPrefill?.takeIf { it.isNotEmpty() }
            val prefillEvent = prefillText?.let { generatePrefillEvent(it) }
            withContext(dispatchers.main()) {
                _viewState.update {
                    // The context has already been captured in contextPrompt above, so drop the
                    // page-context chip from the composer once the prompt is sent.
                    it.copy(showContext = false)
                }
                _subscriptionEventDataChannel.trySend(contextPrompt)
                prefillEvent?.let { _subscriptionEventDataChannel.trySend(it) }
                // Always pass a non-null prefill: a draft preserves the typed text, empty clears the native
                // chat input so stale text from a previous interaction doesn't reappear.
                commandChannel.trySend(
                    Command.ChangeSheetState(
                        newState = BottomSheetBehavior.STATE_EXPANDED,
                        prefillNativeInput = prefillText.orEmpty(),
                    ),
                )
            }
        }
    }

    fun onVoiceRecognitionSuccess(
        query: String,
        isDuckAiResult: Boolean,
    ) {
        if (query.isBlank()) return
        if (isDuckAiResult) {
            onPromptSent(query)
        } else {
            emitCommand(Command.OpenSearchInNewTab(query))
        }
    }

    private fun emitCommand(command: Command) {
        viewModelScope.launch(dispatchers.main()) {
            commandChannel.trySend(command)
        }
    }

    private fun generatePrefillEvent(text: String): SubscriptionEventData {
        val params = JSONObject().apply {
            put("platform", "android")
            put("tool", "query")
            put(
                "query",
                JSONObject().apply {
                    put("prompt", text)
                    put("autoSubmit", false)
                },
            )
        }
        return SubscriptionEventData(
            featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
            subscriptionName = "submitAIChatNativePrompt",
            params = params,
        )
    }

    fun onChatPageLoaded(url: String?) {
        logcat { "Duck.ai: onChatPageLoaded $url" }
        if (url == null) return
        setSheetUrl(url)
        val hasChatId = hasChatId(url)

        viewModelScope.launch {
            _viewState.update { current -> current.copy(showFullscreen = hasChatId) }
        }

        if (hasChatId) {
            val tabId = _viewState.value.tabId
            if (tabId.isNotBlank()) {
                viewModelScope.launch(dispatchers.io()) {
                    contextualDataStore.persistTabChatUrl(tabId, url)
                }
            }
        }
    }

    private fun generateContextPrompt(
        prompt: String,
        modelId: String? = null,
        reasoningEffort: String? = null,
        selectedTool: String? = null,
        imagesJson: JSONArray? = null,
        filesJson: JSONArray? = null,
    ): SubscriptionEventData {
        val viewState = _viewState.value
        val pageContext =
            if (viewState.showContext) {
                pageContextState.attachedPage
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?: run {
                        logcat { "Duck.ai: no pageContext available, skipping pageContext in prompt" }
                        null
                    }
            } else {
                null
            }

        if (pageContext == null) {
            duckChatPixels.reportContextualPromptSubmittedWithoutContextNative()
        } else {
            duckChatPixels.reportContextualPromptSubmittedWithContextNative()
        }

        // The unified input widget is the source of truth for model/reasoning/tool/attachments when it
        // is the composer; fall back to the shared model manager for callers that don't pass them.
        val resolvedModelId = modelId ?: modelManager.getSelectedModelId()
        val resolvedReasoningEffort = reasoningEffort ?: modelManager.getResolvedReasoningEffort()
        val params =
            JSONObject().apply {
                put("platform", "android")
                put("tool", "query")
                put(
                    "query",
                    JSONObject().apply {
                        put("prompt", prompt)
                        put("autoSubmit", true)
                        if (resolvedModelId != null) {
                            put("modelId", resolvedModelId)
                        }
                        if (resolvedReasoningEffort != null) {
                            put("reasoningEffort", resolvedReasoningEffort)
                        }
                        if (selectedTool != null) {
                            put("toolChoice", JSONArray().apply { put(selectedTool) })
                        }
                        if (imagesJson != null) {
                            put("images", imagesJson)
                        }
                        if (filesJson != null) {
                            put("files", filesJson)
                        }
                    },
                )
                pageContext?.let { put("pageContext", it) }
            }

        return SubscriptionEventData(
            featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
            subscriptionName = "submitAIChatNativePrompt",
            params = params,
        )
    }

    private fun generatePageContextEventData(): SubscriptionEventData {
        val pageContext = if (isContextValid(currentPageContext)) {
            JSONObject(currentPageContext)
        } else {
            logcat { "Duck.ai: pageContext is not valid" }
            null
        }

        val params = JSONObject().apply {
            if (duckChatInternal.isAutomaticContextAttachmentEnabled()) {
                put("pageContext", pageContext)
            } else {
                put("pageContext", null)
            }
        }
        return SubscriptionEventData(
            featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
            subscriptionName = "submitAIChatPageContext",
            params = params,
        )
    }

    fun onContextualClose() {
        viewModelScope.launch(dispatchers.main()) {
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HIDDEN))
        }
    }

    fun onSheetClosed() {
        if (hidingSheetForNewChat) {
            // New Chat hid the sheet only to hand off to the entry dialog — not a user dismissal. Skip
            // onContextualClosed (which would revert the tab's contextual input state and strip the entry
            // dialog composer's affordances) and the dismissed pixel. The STATE_HIDDEN callback lands
            // after the sheet's settle animation, so this consume happens well after the dialog opened.
            hidingSheetForNewChat = false
            return
        }
        persistTabClosed()
        duckChatPixels.reportContextualSheetDismissed()
        commandChannel.trySend(Command.ApplyContextualClosed(_viewState.value.tabId))
    }

    private fun persistTabClosed() {
        viewModelScope.launch(dispatchers.io()) {
            val tabId = _viewState.value.tabId
            if (tabId.isNotBlank()) {
                contextualDataStore.persistTabClosedTimestamp(tabId, timeProvider.currentTimeMillis())
            }
        }
    }

    fun removePageContext() {
        logcat { "Duck.ai Contextual: removePageContext" }
        _viewState.update { current ->
            current.copy(showContext = false)
        }
        duckChatPixels.reportContextualPageContextRemovedNative()
    }

    fun addPageContext() {
        logcat { "Duck.ai Contextual: addPageContext" }
        viewModelScope.launch {
            if (isContextValid(currentPageContext, reportInvalidPixels = true)) {
                duckChatPixels.reportContextualPageContextManuallyAttachedNative()
                attachCurrentPageContext()
            }
        }
    }

    private fun attachCurrentPageContext() {
        pageContextState = pageContextState.copy(attachedPage = pageContextState.currentPage)
        val json = JSONObject(currentPageContext)
        _viewState.update { current ->
            current.copy(
                showContext = true,
                contextTitle = json.optString("title"),
                contextUrl = json.optString("url"),
            )
        }
    }

    private fun isContextValid(
        pageContext: String,
        reportInvalidPixels: Boolean = false,
    ): Boolean {
        if (pageContext.isEmpty()) {
            logcat { "Duck.ai: pageContext is empty" }
            if (reportInvalidPixels) duckChatPixels.reportContextualPageContextInvalidEmpty()
            return false
        }
        val json = try {
            JSONObject(pageContext)
        } catch (_: JSONException) {
            logcat { "Duck.ai: pageContext is not valid JSON" }
            if (reportInvalidPixels) duckChatPixels.reportContextualPageContextCollectionEmpty()
            return false
        }
        val title = json.optString("title").takeIf { it.isNotBlank() }
        val content = json.optString("content").takeIf { it.isNotBlank() }
        if (reportInvalidPixels) {
            if (title == null) duckChatPixels.reportContextualPageContextInvalidNoTitle()
            if (content == null) duckChatPixels.reportContextualPageContextInvalidNoContent()
        }
        return title != null && content != null
    }

    fun onAskAboutPageClicked() {
        if (!isContextValid(currentPageContext)) {
            // Page context not ready/valid; do nothing (and don't fire invalid-context pixels).
            return
        }
        addPageContext()
        commandChannel.trySend(Command.FocusInput)
    }

    fun onFullModeRequested() {
        logcat { "Duck.ai: request fullmode url $fullModeUrl" }
        val chatUrl = fullModeUrl.ifEmpty { duckChat.getDuckChatUrl("", false, sidebar = false) }
        viewModelScope.launch {
            commandChannel.trySend(Command.OpenFullscreenMode(chatUrl))
        }
        duckChatPixels.reportContextualSheetExpanded()
    }

    fun onPageContextReceived(
        tabId: String,
        pageContext: String,
    ) {
        if (isContextValid(pageContext)) {
            currentPageContext = pageContext
        }
        if (isContextValid(pageContext, reportInvalidPixels = true)) {
            val json = JSONObject(pageContext)
            val title = json.optString("title")
            val url = json.optString("url")

            logcat { "Duck.ai: onPageContextReceived for url $url" }
            _viewState.update { current ->
                current.copy(
                    contextTitle = title,
                    contextUrl = url,
                    tabId = tabId,
                )
            }
            if (duckChatInternal.isAutomaticContextAttachmentEnabled() || duckChatInternal.areMultipleContentAttachmentsEnabled()) {
                val pageContextEvent = generatePageContextEventData()
                viewModelScope.launch(dispatchers.main()) {
                    _subscriptionEventDataChannel.trySend(pageContextEvent)
                }
            }
        }
    }

    fun handleJSCall(method: String): Boolean {
        return when (method) {
            RealDuckChatJSHelper.METHOD_CLOSE_AI_CHAT -> {
                logcat { "Duck.ai: $method handled at the VM level" }
                onContextualClose()
                true
            }

            else -> false
        }
    }

    fun onNewChatRequestedFromPopup() {
        // Hand New Chat off to the transparent entry dialog: clear the current chat's stored status now
        // so it isn't resumed, then let the dialog command hide the sheet. Mark the impending hide as a
        // handoff so onSheetClosed doesn't revert the tab's contextual input state.
        duckChatPixels.reportContextualSheetNewChatFromPopup()
        hidingSheetForNewChat = true
        resetToNewChat()
        commandChannel.trySend(Command.ShowNewChatEntryDialog(_viewState.value.tabId))
    }

    fun onChatsIconClicked() {
        val state = _viewState.value
        logcat { "Duck.ai Contextual: onChatsIconClicked recentChats=${state.recentChats.size}" }
        duckChatPixels.reportContextualChatsMenuTapped()
        if (state.recentChats.isEmpty()) {
            // No recent chats means there's no popup to show, so go straight to chat history.
            commandChannel.trySend(Command.LaunchChatHistory)
        } else {
            duckChatPixels.reportContextualRecentChatsPopupDisplayed()
            commandChannel.trySend(Command.ShowChatsPopup(recentChats = state.recentChats))
        }
    }

    fun onRecentChatClicked(chatId: String) {
        duckChatPixels.reportContextualRecentChatSelected()
        val url = duckChatInternal.buildChatUrl(chatId)
        val sourceTabId = _viewState.value.tabId
        commandChannel.trySend(Command.OpenChatUrl(url = url, sourceTabId = sourceTabId))
    }

    fun onViewAllChatsClicked() {
        duckChatPixels.reportContextualViewAllChatsTapped()
        commandChannel.trySend(Command.LaunchChatHistory)
    }

    fun onFireButtonClicked() {
        duckChatPixels.reportContextualFireButtonTapped()
        viewModelScope.launch {
            commandChannel.trySend(Command.ShowFireConfirmation)
        }
    }

    fun onContextualFireConfirmed() {
        duckChatPixels.reportContextualFireButtonConfirmed()
        resetToNewChat()
        commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HIDDEN))
    }

    // Clears the current chat and resets attachment/context so the next open starts fresh, without
    // touching the sheet position (callers decide what to do with the sheet). Fires the NEW_CHAT
    // native action so the web app resets too.
    private fun resetToNewChat() {
        viewModelScope.launch(dispatchers.io()) {
            val currentTabId = _viewState.value.tabId
            if (currentTabId.isBlank()) return@launch
            contextualDataStore.clearTabChatUrl(currentTabId)
            withContext(dispatchers.main()) {
                clearSheetUrl()
                pageContextState = pageContextState.copy(attachedPage = "")
                _viewState.update {
                    it.copy(
                        showFullscreen = true,
                        showContext = false,
                    )
                }
                val subscriptionEvent = duckChatJSHelper.onNativeAction(NativeAction.NEW_CHAT)
                _subscriptionEventDataChannel.trySend(subscriptionEvent)
            }
        }
    }

    private fun hasChatId(url: String?): Boolean = !extractChatId(url).isNullOrBlank()

    private fun extractChatId(url: String?): String? {
        val uri = url?.toUri() ?: return null
        return uri.toChatIdOrNull(duckChat)
    }

    // Owns the coupled (fullModeUrl, _chatId) invariant: _chatId is always extractChatId(fullModeUrl).
    private fun setSheetUrl(url: String) {
        fullModeUrl = url
        _chatId.value = extractChatId(url)
    }

    private fun clearSheetUrl() {
        fullModeUrl = ""
        _chatId.value = null
    }

    private suspend fun shouldReuseStoredChatUrl(tabId: String): Boolean {
        val lastClosedTimestamp = contextualDataStore.getTabClosedTimestamp(tabId) ?: return true
        val timeoutMs = sessionTimeoutProvider.sessionTimeoutMillis()
        if (timeoutMs <= 0) return false
        val elapsedMs = timeProvider.currentTimeMillis() - lastClosedTimestamp
        return elapsedMs <= timeoutMs
    }

    companion object {
        const val MAX_RECENT_CHATS = 5
    }
}
