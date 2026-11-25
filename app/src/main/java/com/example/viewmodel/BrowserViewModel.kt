package com.example.viewmodel

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DuckDuckGoApplication
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.FireproofSiteEntity
import com.example.data.local.HistoryEntity
import com.example.model.AutocompleteSuggestion
import com.example.model.BlockedTracker
import com.example.model.BrowserSettings
import com.example.model.BrowserTab
import com.example.model.OmnibarPosition
import com.example.model.SuggestionType
import com.example.model.ThemeMode
import com.example.privacy.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface WebViewCommand {
    data class LoadUrl(val tabId: String, val url: String, val gpcEnabled: Boolean) : WebViewCommand
    data class GoBack(val tabId: String) : WebViewCommand
    data class GoForward(val tabId: String) : WebViewCommand
    data class Reload(val tabId: String) : WebViewCommand
    data class StopLoading(val tabId: String) : WebViewCommand
    data class SetDesktopMode(val tabId: String, val enabled: Boolean) : WebViewCommand
    data class FindInPage(val tabId: String, val query: String) : WebViewCommand
    data class FindNext(val tabId: String, val forward: Boolean) : WebViewCommand
    data class ClearFindInPage(val tabId: String) : WebViewCommand
    data class PrintPage(val tabId: String) : WebViewCommand
    data class DisposeTab(val tabId: String) : WebViewCommand
    data object DisposeAllTabs : WebViewCommand
}

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabIndex: Int = 0,
    val omnibarText: String = "",
    val isOmnibarFocused: Boolean = false,
    val suggestions: List<AutocompleteSuggestion> = emptyList(),
    val isFindInPageVisible: Boolean = false,
    val findInPageQuery: String = "",
    val findActiveMatch: Int = 0,
    val findTotalMatches: Int = 0,
    val isFireAnimationPlaying: Boolean = false,
    val isGridTabLayout: Boolean = true,
) {
    val activeTab: BrowserTab
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DuckDuckGoApplication
    private val db = app.database
    private val prefs = app.preferences

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _webCommands = MutableSharedFlow<WebViewCommand>(extraBufferCapacity = 32)
    val webCommands = _webCommands.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    val settings: StateFlow<BrowserSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserSettings(),
    )

    val disabledPrivacyDomains: StateFlow<Set<String>> = prefs.disabledPrivacyDomainsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = db.bookmarkDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val favorites: StateFlow<List<BookmarkEntity>> = db.bookmarkDao().observeFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val history: StateFlow<List<HistoryEntity>> = db.historyDao().observeRecent().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val fireproofSites: StateFlow<List<FireproofSiteEntity>> = db.fireproofSiteDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val downloads: StateFlow<List<DownloadEntity>> = db.downloadDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private var suggestionJob: Job? = null

    init {
        seedInitialFavoritesIfFirstLaunch()
    }

    private fun seedInitialFavoritesIfFirstLaunch() {
        viewModelScope.launch(Dispatchers.IO) {
            val shouldSeed = prefs.markDefaultsSeeded()
            if (shouldSeed && db.bookmarkDao().count() == 0) {
                val defaults = listOf(
                    BookmarkEntity(title = "DuckDuckGo", url = "https://duckduckgo.com", isFavorite = true),
                    BookmarkEntity(title = "Wikipedia", url = "https://en.wikipedia.org", isFavorite = true),
                    BookmarkEntity(title = "Privacy Test Pages", url = "https://privacy-test-pages.site", isFavorite = true),
                    BookmarkEntity(title = "GitHub", url = "https://github.com", isFavorite = true),
                )
                defaults.forEach { db.bookmarkDao().upsert(it) }
            }
        }
    }

    fun onOmnibarFocusChanged(focused: Boolean) {
        _uiState.update { state ->
            val nextText = if (focused) state.activeTab.url else state.activeTab.url
            state.copy(
                isOmnibarFocused = focused,
                omnibarText = nextText,
                suggestions = if (!focused) emptyList() else state.suggestions,
            )
        }
        if (focused && _uiState.value.omnibarText.isNotBlank()) {
            refreshSuggestions(_uiState.value.omnibarText)
        }
    }

    fun onOmnibarTextChanged(text: String) {
        _uiState.update { it.copy(omnibarText = text) }
        refreshSuggestions(text)
    }

    private fun refreshSuggestions(query: String) {
        suggestionJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        suggestionJob = viewModelScope.launch {
            delay(140)
            val results = mutableListOf<AutocompleteSuggestion>()

            if (PrivacyEngine.isUrlLike(trimmed)) {
                val (targetUrl, _) = PrivacyEngine.buildTargetUrl(
                    trimmed,
                    httpsUpgradesEnabled = settings.value.httpsUpgradesEnabled,
                    stripTrackingParamsEnabled = settings.value.stripTrackingParamsEnabled,
                )
                results += AutocompleteSuggestion(
                    title = trimmed,
                    subtitle = targetUrl,
                    targetUrl = targetUrl,
                    type = SuggestionType.DIRECT_URL,
                )
            }

            val bookmarkMatches = withContext(Dispatchers.IO) { db.bookmarkDao().search(trimmed) }
            bookmarkMatches.forEach { bm ->
                results += AutocompleteSuggestion(
                    title = bm.title,
                    subtitle = bm.url,
                    targetUrl = bm.url,
                    type = SuggestionType.BOOKMARK,
                )
            }

            val historyMatches = withContext(Dispatchers.IO) { db.historyDao().search(trimmed) }
            historyMatches
                .distinctBy { it.url }
                .filterNot { hist -> results.any { it.targetUrl == hist.url } }
                .take(3)
                .forEach { hist ->
                    results += AutocompleteSuggestion(
                        title = hist.title.ifBlank { hist.url },
                        subtitle = hist.url,
                        targetUrl = hist.url,
                        type = SuggestionType.HISTORY,
                    )
                }

            val remoteSuggestions = fetchRemoteSuggestions(trimmed)
            remoteSuggestions
                .filterNot { phrase -> results.any { it.title.equals(phrase, ignoreCase = true) } }
                .take(5)
                .forEach { phrase ->
                    val encoded = URLEncoder.encode(phrase, StandardCharsets.UTF_8.toString())
                    results += AutocompleteSuggestion(
                        title = phrase,
                        subtitle = "Search DuckDuckGo",
                        targetUrl = "https://duckduckgo.com/?q=$encoded",
                        type = SuggestionType.SEARCH,
                    )
                }

            if (results.none { it.type == SuggestionType.SEARCH && it.title.equals(trimmed, ignoreCase = true) }) {
                val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
                results += AutocompleteSuggestion(
                    title = trimmed,
                    subtitle = "Search DuckDuckGo",
                    targetUrl = "https://duckduckgo.com/?q=$encoded",
                    type = SuggestionType.SEARCH,
                )
            }

            _uiState.update { it.copy(suggestions = results.take(8)) }
        }
    }

    private suspend fun fetchRemoteSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val conn = (URL("https://duckduckgo.com/ac/?q=$encoded&type=list").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "DuckDuckGo-Android")
            }
            conn.inputStream.bufferedReader().use { reader ->
                val raw = reader.readText()
                val root = JSONArray(raw)
                val phrases = if (root.length() >= 2) root.getJSONArray(1) else JSONArray()
                buildList {
                    for (i in 0 until phrases.length()) {
                        add(phrases.getString(i))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun submitOmnibar(rawInput: String = _uiState.value.omnibarText) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val currentSettings = settings.value
        val (targetUrl, upgraded) = PrivacyEngine.buildTargetUrl(
            trimmed,
            httpsUpgradesEnabled = currentSettings.httpsUpgradesEnabled,
            stripTrackingParamsEnabled = currentSettings.stripTrackingParamsEnabled,
        )
        navigateActiveTab(targetUrl, httpsUpgraded = upgraded)
    }

    fun selectSuggestion(suggestion: AutocompleteSuggestion) {
        navigateActiveTab(suggestion.targetUrl, httpsUpgraded = false)
    }

    fun openUrlInActiveTab(url: String) {
        val currentSettings = settings.value
        val (targetUrl, upgraded) = PrivacyEngine.buildTargetUrl(
            url,
            httpsUpgradesEnabled = currentSettings.httpsUpgradesEnabled,
            stripTrackingParamsEnabled = currentSettings.stripTrackingParamsEnabled,
        )
        navigateActiveTab(targetUrl, httpsUpgraded = upgraded)
    }

    fun openUrlInNewTab(url: String, isFireTab: Boolean = false) {
        val currentSettings = settings.value
        val (targetUrl, upgraded) = PrivacyEngine.buildTargetUrl(
            url,
            httpsUpgradesEnabled = currentSettings.httpsUpgradesEnabled,
            stripTrackingParamsEnabled = currentSettings.stripTrackingParamsEnabled,
        )
        val newTab = BrowserTab(
            url = targetUrl,
            title = PrivacyEngine.extractHost(targetUrl) ?: targetUrl,
            isFireTab = isFireTab,
            isSecure = targetUrl.startsWith("https://"),
            httpsUpgraded = upgraded,
        )
        _uiState.update { state ->
            val updatedTabs = state.tabs + newTab
            state.copy(
                tabs = updatedTabs,
                activeTabIndex = updatedTabs.lastIndex,
                omnibarText = targetUrl,
                isOmnibarFocused = false,
                suggestions = emptyList(),
                isFindInPageVisible = false,
            )
        }
        if (upgraded) {
            viewModelScope.launch { prefs.incrementHttpsUpgrades() }
        }
        _webCommands.tryEmit(WebViewCommand.LoadUrl(newTab.id, targetUrl, currentSettings.gpcEnabled))
    }

    private fun navigateActiveTab(targetUrl: String, httpsUpgraded: Boolean) {
        val tab = _uiState.value.activeTab
        val currentSettings = settings.value
        _uiState.update { state ->
            val updated = state.tabs.mapIndexed { idx, item ->
                if (idx == state.activeTabIndex) {
                    item.copy(
                        url = targetUrl,
                        title = PrivacyEngine.extractHost(targetUrl) ?: targetUrl,
                        isLoading = true,
                        progress = 10,
                        isSecure = targetUrl.startsWith("https://"),
                        httpsUpgraded = httpsUpgraded,
                        blockedTrackers = emptyList(),
                        lastAccessedAt = System.currentTimeMillis(),
                    )
                } else {
                    item
                }
            }
            state.copy(
                tabs = updated,
                omnibarText = targetUrl,
                isOmnibarFocused = false,
                suggestions = emptyList(),
                isFindInPageVisible = false,
            )
        }
        if (httpsUpgraded) {
            viewModelScope.launch { prefs.incrementHttpsUpgrades() }
        }
        _webCommands.tryEmit(WebViewCommand.LoadUrl(tab.id, targetUrl, currentSettings.gpcEnabled))
    }

    fun onPageStarted(tabId: String, url: String) {
        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        isLoading = true,
                        progress = 15,
                        isSecure = url.startsWith("https://"),
                        blockedTrackers = emptyList(),
                    )
                } else {
                    tab
                }
            }
            val activeUrl = updated.getOrNull(state.activeTabIndex)?.url ?: state.omnibarText
            state.copy(
                tabs = updated,
                omnibarText = if (state.isOmnibarFocused) state.omnibarText else activeUrl,
            )
        }
    }

    fun onPageProgress(tabId: String, progress: Int) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(progress = progress, isLoading = progress < 100) else tab
                },
            )
        }
    }

    fun onPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
        val cleanTitle = title.ifBlank { PrivacyEngine.extractHost(url) ?: url }
        var isFireTab = false
        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    isFireTab = tab.isFireTab
                    tab.copy(
                        url = url,
                        title = cleanTitle,
                        isLoading = false,
                        progress = 100,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isSecure = url.startsWith("https://"),
                        lastAccessedAt = System.currentTimeMillis(),
                    )
                } else {
                    tab
                }
            }
            val activeUrl = updated.getOrNull(state.activeTabIndex)?.url ?: state.omnibarText
            state.copy(
                tabs = updated,
                omnibarText = if (state.isOmnibarFocused) state.omnibarText else activeUrl,
            )
        }

        if (!isFireTab && url.startsWith("http")) {
            viewModelScope.launch(Dispatchers.IO) {
                db.historyDao().insert(HistoryEntity(title = cleanTitle, url = url))
            }
        }
    }

    fun onTrackerBlocked(tabId: String, tracker: BlockedTracker) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        if (tab.blockedTrackers.size < 200) {
                            tab.copy(blockedTrackers = tab.blockedTrackers + tracker)
                        } else {
                            tab
                        }
                    } else {
                        tab
                    }
                },
            )
        }
        viewModelScope.launch { prefs.incrementTrackersBlocked() }
    }

    fun isPrivacyProtectionActiveForUrl(url: String?): Boolean {
        if (!settings.value.trackerBlockingEnabled) return false
        val host = url?.let { PrivacyEngine.extractHost(it) } ?: return true
        return !disabledPrivacyDomains.value.contains(host)
    }

    fun goBack() {
        _webCommands.tryEmit(WebViewCommand.GoBack(_uiState.value.activeTab.id))
    }

    fun goForward() {
        _webCommands.tryEmit(WebViewCommand.GoForward(_uiState.value.activeTab.id))
    }

    fun reloadOrStop() {
        val tab = _uiState.value.activeTab
        if (tab.isLoading) {
            _webCommands.tryEmit(WebViewCommand.StopLoading(tab.id))
        } else {
            _webCommands.tryEmit(WebViewCommand.Reload(tab.id))
        }
    }

    fun toggleDesktopMode() {
        val tab = _uiState.value.activeTab
        val next = !tab.isDesktopMode
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.mapIndexed { idx, item ->
                    if (idx == state.activeTabIndex) item.copy(isDesktopMode = next) else item
                },
            )
        }
        _webCommands.tryEmit(WebViewCommand.SetDesktopMode(tab.id, next))
    }

    fun printCurrentPage() {
        val tab = _uiState.value.activeTab
        if (!tab.isNewTabPage) {
            _webCommands.tryEmit(WebViewCommand.PrintPage(tab.id))
        }
    }

    // Find in Page
    fun showFindInPage() {
        _uiState.update { it.copy(isFindInPageVisible = true, findInPageQuery = "", findActiveMatch = 0, findTotalMatches = 0) }
    }

    fun hideFindInPage() {
        val tabId = _uiState.value.activeTab.id
        _uiState.update { it.copy(isFindInPageVisible = false, findInPageQuery = "", findActiveMatch = 0, findTotalMatches = 0) }
        _webCommands.tryEmit(WebViewCommand.ClearFindInPage(tabId))
    }

    fun updateFindQuery(query: String) {
        val tabId = _uiState.value.activeTab.id
        _uiState.update { it.copy(findInPageQuery = query) }
        _webCommands.tryEmit(WebViewCommand.FindInPage(tabId, query))
    }

    fun findNext(forward: Boolean) {
        _webCommands.tryEmit(WebViewCommand.FindNext(_uiState.value.activeTab.id, forward))
    }

    fun onFindMatchesUpdated(activeOrdinal: Int, count: Int) {
        _uiState.update {
            it.copy(
                findActiveMatch = if (count == 0) 0 else activeOrdinal + 1,
                findTotalMatches = count,
            )
        }
    }

    // Tab Management
    fun openNewTab(isFireTab: Boolean = false) {
        val current = _uiState.value.activeTab
        if (current.isNewTabPage && current.isFireTab == isFireTab) {
            _uiState.update { it.copy(isOmnibarFocused = true, omnibarText = "") }
            return
        }
        val tab = BrowserTab(isFireTab = isFireTab)
        _uiState.update { state ->
            val updated = state.tabs + tab
            state.copy(
                tabs = updated,
                activeTabIndex = updated.lastIndex,
                omnibarText = "",
                isOmnibarFocused = false,
                suggestions = emptyList(),
                isFindInPageVisible = false,
            )
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            val safeIndex = index.coerceIn(0, state.tabs.lastIndex)
            val tab = state.tabs[safeIndex]
            state.copy(
                activeTabIndex = safeIndex,
                omnibarText = tab.url,
                isOmnibarFocused = false,
                suggestions = emptyList(),
                isFindInPageVisible = false,
            )
        }
    }

    fun closeTab(tabId: String) {
        _webCommands.tryEmit(WebViewCommand.DisposeTab(tabId))
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                val fresh = BrowserTab()
                state.copy(
                    tabs = listOf(fresh),
                    activeTabIndex = 0,
                    omnibarText = "",
                    isOmnibarFocused = false,
                    suggestions = emptyList(),
                )
            } else {
                val removedIdx = state.tabs.indexOfFirst { it.id == tabId }
                val remaining = state.tabs.filterNot { it.id == tabId }
                val newIdx = when {
                    removedIdx < 0 -> state.activeTabIndex.coerceIn(0, remaining.lastIndex)
                    removedIdx < state.activeTabIndex -> state.activeTabIndex - 1
                    state.activeTabIndex >= remaining.size -> remaining.lastIndex
                    else -> state.activeTabIndex
                }
                state.copy(
                    tabs = remaining,
                    activeTabIndex = newIdx,
                    omnibarText = remaining[newIdx].url,
                    isOmnibarFocused = false,
                    suggestions = emptyList(),
                )
            }
        }
    }

    fun closeAllTabs() {
        _webCommands.tryEmit(WebViewCommand.DisposeAllTabs)
        _uiState.update {
            BrowserUiState(
                tabs = listOf(BrowserTab()),
                activeTabIndex = 0,
                isGridTabLayout = it.isGridTabLayout,
            )
        }
    }

    fun toggleTabLayoutMode() {
        _uiState.update { it.copy(isGridTabLayout = !it.isGridTabLayout) }
    }

    // Fire Button Data Clearing
    fun deleteCurrentTabWithFire() {
        val tab = _uiState.value.activeTab
        _webCommands.tryEmit(WebViewCommand.DisposeTab(tab.id))
        _uiState.update { state ->
            val fresh = BrowserTab(id = tab.id, isFireTab = tab.isFireTab)
            val updated = state.tabs.mapIndexed { idx, item ->
                if (idx == state.activeTabIndex) fresh else item
            }
            state.copy(
                tabs = updated,
                omnibarText = "",
                isOmnibarFocused = false,
                suggestions = emptyList(),
                isFindInPageVisible = false,
            )
        }
        emitMessage("Tab data cleared")
    }

    fun executeFireBurnAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFireAnimationPlaying = true) }
            _webCommands.tryEmit(WebViewCommand.DisposeAllTabs)

            val fireproofDomains = withContext(Dispatchers.IO) {
                db.fireproofSiteDao().getAllDomains()
            }

            clearBrowsingDataPreservingFireproof(fireproofDomains)
            delay(950)

            _uiState.update {
                BrowserUiState(
                    tabs = listOf(BrowserTab()),
                    activeTabIndex = 0,
                    isFireAnimationPlaying = false,
                    isGridTabLayout = it.isGridTabLayout,
                )
            }
            emitMessage("Tabs and browsing data cleared")
        }
    }

    private suspend fun clearBrowsingDataPreservingFireproof(fireproofDomains: List<String>) {
        withContext(Dispatchers.Main) {
            runCatching {
                val cookieManager = CookieManager.getInstance()
                if (fireproofDomains.isEmpty()) {
                    cookieManager.removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                } else {
                    // Preserve cookies for Fireproof domains by re-saving or skipping full wipe if needed;
                    // WebView API lacks selective remove-except, so we backup & restore cookies for fireproofed domains.
                    val savedCookies = fireproofDomains.associateWith { domain ->
                        cookieManager.getCookie("https://$domain").orEmpty()
                    }
                    cookieManager.removeAllCookies {
                        savedCookies.forEach { (domain, cookieHeader) ->
                            if (cookieHeader.isNotBlank()) {
                                cookieHeader.split(";").forEach { part ->
                                    cookieManager.setCookie("https://$domain", part.trim())
                                }
                            }
                        }
                        cookieManager.flush()
                    }
                    WebStorage.getInstance().deleteAllData()
                }
                cookieManager.flush()
                WebViewDatabase.getInstance(app).clearFormData()
                WebView(app).clearCache(true)
            }
        }
        withContext(Dispatchers.IO) {
            db.historyDao().clearAll()
        }
    }

    // Bookmarks & Favorites CRUD
    fun toggleBookmarkForActiveTab() {
        val tab = _uiState.value.activeTab
        if (tab.isNewTabPage) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.bookmarkDao().getByUrl(tab.url)
            if (existing != null) {
                db.bookmarkDao().deleteById(existing.id)
                emitMessage("Bookmark removed")
            } else {
                db.bookmarkDao().upsert(BookmarkEntity(title = tab.title, url = tab.url, isFavorite = false))
                emitMessage("Saved to Bookmarks")
            }
        }
    }

    fun toggleFavoriteForActiveTab() {
        val tab = _uiState.value.activeTab
        if (tab.isNewTabPage) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.bookmarkDao().getByUrl(tab.url)
            if (existing != null) {
                val nextFav = !existing.isFavorite
                db.bookmarkDao().upsert(existing.copy(isFavorite = nextFav))
                emitMessage(if (nextFav) "Pinned to Favorites" else "Removed from Favorites")
            } else {
                db.bookmarkDao().upsert(BookmarkEntity(title = tab.title, url = tab.url, isFavorite = true))
                emitMessage("Pinned to Favorites")
            }
        }
    }

    fun saveBookmark(id: Long = 0L, title: String, url: String, isFavorite: Boolean) {
        if (url.isBlank()) return
        val normalized = if (url.startsWith("http")) url.trim() else "https://${url.trim()}"
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().upsert(
                BookmarkEntity(
                    id = id,
                    title = title.ifBlank { PrivacyEngine.extractHost(normalized) ?: normalized },
                    url = normalized,
                    isFavorite = isFavorite,
                ),
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().deleteById(id)
        }
    }

    // History CRUD
    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().deleteById(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().clearAll()
            emitMessage("History cleared")
        }
    }

    // Fireproof Sites CRUD
    fun toggleFireproofForActiveTab() {
        val host = PrivacyEngine.extractHost(_uiState.value.activeTab.url) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.fireproofSiteDao().getAllDomains()
            if (existing.contains(host)) {
                db.fireproofSiteDao().delete(host)
                emitMessage("Removed $host from Fireproof Sites")
            } else {
                db.fireproofSiteDao().insert(FireproofSiteEntity(domain = host))
                emitMessage("Fireproofed $host")
            }
        }
    }

    fun addFireproofSite(domain: String) {
        val clean = PrivacyEngine.extractHost(if (domain.startsWith("http")) domain else "https://$domain") ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.fireproofSiteDao().insert(FireproofSiteEntity(domain = clean))
        }
    }

    fun removeFireproofSite(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.fireproofSiteDao().delete(domain)
        }
    }

    // Privacy Protection per-site toggle
    fun togglePrivacyProtectionForActiveTab() {
        val host = PrivacyEngine.extractHost(_uiState.value.activeTab.url) ?: return
        viewModelScope.launch {
            prefs.toggleSitePrivacyProtection(host)
            _webCommands.tryEmit(WebViewCommand.Reload(_uiState.value.activeTab.id))
        }
    }

    fun reportBrokenSite(domain: String) {
        emitMessage("Thank you! Privacy report noted for $domain")
    }

    // Downloads
    fun recordDownload(fileName: String, url: String, mimeType: String, sizeBytes: Long, systemDownloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.downloadDao().insert(
                DownloadEntity(
                    fileName = fileName,
                    url = url,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    systemDownloadId = systemDownloadId,
                ),
            )
            emitMessage("Download started: $fileName")
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.downloadDao().deleteById(id) }
    }

    fun clearDownloads() {
        viewModelScope.launch(Dispatchers.IO) { db.downloadDao().clearAll() }
    }

    // Settings
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setOmnibarPosition(position: OmnibarPosition) {
        viewModelScope.launch { prefs.setOmnibarPosition(position) }
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setTrackerBlockingEnabled(enabled) }
    }

    fun setHttpsUpgradesEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHttpsUpgradesEnabled(enabled) }
    }

    fun setStripTrackingParamsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setStripTrackingParamsEnabled(enabled) }
    }

    fun setGpcEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setGpcEnabled(enabled) }
    }

    fun setYoutubeAdBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setYoutubeAdBlockingEnabled(enabled) }
    }

    fun setAutoClearOnExit(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoClearOnExit(enabled) }
    }

    fun clearDataIfAutoClearEnabled() {
        if (settings.value.autoClearOnExit) {
            viewModelScope.launch {
                val fireproof = withContext(Dispatchers.IO) { db.fireproofSiteDao().getAllDomains() }
                clearBrowsingDataPreservingFireproof(fireproof)
            }
        }
    }

    private fun emitMessage(text: String) {
        _messages.tryEmit(text)
    }
}
