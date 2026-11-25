package com.example.model

import java.util.UUID

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class OmnibarPosition {
    TOP,
    BOTTOM,
}

enum class TrackerCategory(val displayName: String) {
    ADVERTISING("Advertising"),
    ANALYTICS("Analytics"),
    SOCIAL("Social Tracking"),
    FINGERPRINTING("Fingerprinting"),
    OTHER("Tracker"),
}

data class BlockedTracker(
    val domain: String,
    val company: String,
    val category: TrackerCategory,
    val timestamp: Long = System.currentTimeMillis(),
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val isFireTab: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val httpsUpgraded: Boolean = false,
    val blockedTrackers: List<BlockedTracker> = emptyList(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
) {
    val isNewTabPage: Boolean
        get() = url.isBlank()
}

enum class SuggestionType {
    DIRECT_URL,
    BOOKMARK,
    HISTORY,
    SEARCH,
}

data class AutocompleteSuggestion(
    val title: String,
    val subtitle: String,
    val targetUrl: String,
    val type: SuggestionType,
)

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val omnibarPosition: OmnibarPosition = OmnibarPosition.TOP,
    val trackerBlockingEnabled: Boolean = true,
    val httpsUpgradesEnabled: Boolean = true,
    val stripTrackingParamsEnabled: Boolean = true,
    val gpcEnabled: Boolean = true,
    val youtubeAdBlockingEnabled: Boolean = true,
    val autoClearOnExit: Boolean = false,
    val totalTrackersBlocked: Long = 0L,
    val totalHttpsUpgrades: Long = 0L,
)
