package com.example.ui.browser

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.OmnibarPosition
import com.example.privacy.PrivacyEngine
import com.example.ui.sheets.BrowserMenuSheet
import com.example.ui.sheets.FireAnimationOverlay
import com.example.ui.sheets.FireDialogSheet
import com.example.ui.sheets.PrivacyDashboardSheet
import com.example.viewmodel.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateToTabs: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacySettings: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val fireproofSites by viewModel.fireproofSites.collectAsStateWithLifecycle()
    val disabledPrivacyDomains by viewModel.disabledPrivacyDomains.collectAsStateWithLifecycle()

    var showMenuSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showFireSheet by remember { mutableStateOf(false) }

    val activeTab = uiState.activeTab
    val activeHost = PrivacyEngine.extractHost(activeTab.url)
    val isBookmarked = remember(bookmarks, activeTab.url) {
        bookmarks.any { it.url == activeTab.url }
    }
    val isFavorite = remember(bookmarks, activeTab.url) {
        bookmarks.any { it.url == activeTab.url && it.isFavorite }
    }
    val isFireproofed = remember(fireproofSites, activeHost) {
        activeHost != null && fireproofSites.any { it.domain == activeHost }
    }
    val isPrivacyActiveForCurrent = remember(settings.trackerBlockingEnabled, disabledPrivacyDomains, activeHost) {
        settings.trackerBlockingEnabled && (activeHost == null || !disabledPrivacyDomains.contains(activeHost))
    }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = uiState.isFindInPageVisible || uiState.isOmnibarFocused || activeTab.canGoBack) {
        when {
            uiState.isFindInPageVisible -> viewModel.hideFindInPage()
            uiState.isOmnibarFocused -> {
                focusManager.clearFocus()
                viewModel.onOmnibarFocusChanged(false)
            }
            activeTab.canGoBack -> viewModel.goBack()
        }
    }

    val omnibarComposable: @Composable () -> Unit = {
        Column {
            BrowserOmnibar(
                activeTab = activeTab,
                omnibarText = uiState.omnibarText,
                isOmnibarFocused = uiState.isOmnibarFocused,
                tabCount = uiState.tabs.size,
                isPrivacyProtected = isPrivacyActiveForCurrent,
                focusRequester = focusRequester,
                onFocusChanged = viewModel::onOmnibarFocusChanged,
                onTextChanged = viewModel::onOmnibarTextChanged,
                onSubmit = {
                    focusManager.clearFocus()
                    viewModel.submitOmnibar()
                },
                onReloadOrStop = viewModel::reloadOrStop,
                onPrivacyShieldClick = { showPrivacySheet = true },
                onFireClick = { showFireSheet = true },
                onTabsClick = onNavigateToTabs,
                onMenuClick = { showMenuSheet = true },
            )
            FindInPageBar(
                visible = uiState.isFindInPageVisible && !activeTab.isNewTabPage,
                query = uiState.findInPageQuery,
                activeMatch = uiState.findActiveMatch,
                totalMatches = uiState.findTotalMatches,
                onQueryChange = viewModel::updateFindQuery,
                onPrev = { viewModel.findNext(false) },
                onNext = { viewModel.findNext(true) },
                onClose = viewModel::hideFindInPage,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (settings.omnibarPosition == OmnibarPosition.TOP) {
                    omnibarComposable()
                }
            },
            bottomBar = {
                if (settings.omnibarPosition == OmnibarPosition.BOTTOM) {
                    omnibarComposable()
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (activeTab.isNewTabPage) {
                    NewTabPage(
                        isFireTab = activeTab.isFireTab,
                        settings = settings,
                        favorites = bookmarks.filter { it.isFavorite },
                        onSearchBoxClicked = { focusRequester.requestFocus() },
                        onOpenUrl = viewModel::openUrlInActiveTab,
                        onAddFavorite = { title, url ->
                            viewModel.saveBookmark(title = title, url = url, isFavorite = true)
                        },
                        onRemoveFavorite = { fav ->
                            viewModel.saveBookmark(
                                id = fav.id,
                                title = fav.title,
                                url = fav.url,
                                isFavorite = false,
                            )
                        },
                        onOpenPrivacySettings = onNavigateToPrivacySettings,
                    )
                } else {
                    WebViewHost(
                        activeTab = activeTab,
                        settings = settings,
                        isPrivacyActiveForUrl = viewModel::isPrivacyProtectionActiveForUrl,
                        webCommands = viewModel.webCommands,
                        onPageStarted = viewModel::onPageStarted,
                        onPageProgress = viewModel::onPageProgress,
                        onPageFinished = viewModel::onPageFinished,
                        onTrackerBlocked = viewModel::onTrackerBlocked,
                        onFindMatchesUpdated = viewModel::onFindMatchesUpdated,
                        onDownloadStarted = viewModel::recordDownload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (uiState.isOmnibarFocused && uiState.suggestions.isNotEmpty()) {
                    AutocompleteOverlay(
                        suggestions = uiState.suggestions,
                        onSelect = { suggestion ->
                            focusManager.clearFocus()
                            viewModel.selectSuggestion(suggestion)
                        },
                        onFillQuery = { text ->
                            viewModel.onOmnibarTextChanged(text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (showMenuSheet) {
            BrowserMenuSheet(
                activeTab = activeTab,
                isBookmarked = isBookmarked,
                isFavorite = isFavorite,
                isFireproofed = isFireproofed,
                isPrivacyProtected = isPrivacyActiveForCurrent,
                onDismiss = { showMenuSheet = false },
                onBack = {
                    showMenuSheet = false
                    viewModel.goBack()
                },
                onForward = {
                    showMenuSheet = false
                    viewModel.goForward()
                },
                onRefresh = {
                    showMenuSheet = false
                    viewModel.reloadOrStop()
                },
                onNewTab = {
                    showMenuSheet = false
                    viewModel.openNewTab(isFireTab = false)
                },
                onNewFireTab = {
                    showMenuSheet = false
                    viewModel.openNewTab(isFireTab = true)
                },
                onToggleBookmark = {
                    showMenuSheet = false
                    viewModel.toggleBookmarkForActiveTab()
                },
                onToggleFavorite = {
                    showMenuSheet = false
                    viewModel.toggleFavoriteForActiveTab()
                },
                onShare = {
                    showMenuSheet = false
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                        putExtra(Intent.EXTRA_TEXT, activeTab.url)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share page"))
                },
                onFindInPage = {
                    showMenuSheet = false
                    viewModel.showFindInPage()
                },
                onToggleDesktopSite = {
                    showMenuSheet = false
                    viewModel.toggleDesktopMode()
                },
                onToggleFireproof = {
                    showMenuSheet = false
                    viewModel.toggleFireproofForActiveTab()
                },
                onTogglePrivacyProtection = {
                    showMenuSheet = false
                    viewModel.togglePrivacyProtectionForActiveTab()
                },
                onPrint = {
                    showMenuSheet = false
                    viewModel.printCurrentPage()
                },
                onOpenBookmarks = {
                    showMenuSheet = false
                    onNavigateToBookmarks()
                },
                onOpenHistory = {
                    showMenuSheet = false
                    onNavigateToHistory()
                },
                onOpenDownloads = {
                    showMenuSheet = false
                    onNavigateToDownloads()
                },
                onOpenFireDialog = {
                    showMenuSheet = false
                    showFireSheet = true
                },
                onOpenSettings = {
                    showMenuSheet = false
                    onNavigateToSettings()
                },
            )
        }

        if (showPrivacySheet) {
            PrivacyDashboardSheet(
                activeTab = activeTab,
                isProtectionEnabled = isPrivacyActiveForCurrent,
                onDismiss = { showPrivacySheet = false },
                onToggleProtection = {
                    viewModel.togglePrivacyProtectionForActiveTab()
                },
                onReportBrokenSite = {
                    activeHost?.let { viewModel.reportBrokenSite(it) }
                    showPrivacySheet = false
                },
            )
        }

        if (showFireSheet) {
            FireDialogSheet(
                onDismiss = { showFireSheet = false },
                onDeleteThisTab = {
                    showFireSheet = false
                    viewModel.deleteCurrentTabWithFire()
                },
                onDeleteAll = {
                    showFireSheet = false
                    viewModel.executeFireBurnAll()
                },
            )
        }

        FireAnimationOverlay(visible = uiState.isFireAnimationPlaying)
    }
}
