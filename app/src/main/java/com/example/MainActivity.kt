package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.browser.BrowserScreen
import com.example.ui.library.BookmarksScreen
import com.example.ui.library.DownloadsScreen
import com.example.ui.library.HistoryScreen
import com.example.ui.navigation.Routes
import com.example.ui.settings.AppearanceSettingsScreen
import com.example.ui.settings.DataClearingSettingsScreen
import com.example.ui.settings.FireproofSitesScreen
import com.example.ui.settings.PrivacySettingsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tabs.TabSwitcherScreen
import com.example.ui.theme.DuckDuckGoBrowserTheme
import com.example.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            DuckDuckGoBrowserTheme(themeMode = settings.themeMode) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.BROWSER,
                ) {
                    composable(Routes.BROWSER) {
                        BrowserScreen(
                            viewModel = viewModel,
                            onNavigateToTabs = { navController.navigate(Routes.TAB_SWITCHER) },
                            onNavigateToBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                            onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                            onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                            onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                            onNavigateToPrivacySettings = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                        )
                    }
                    composable(Routes.TAB_SWITCHER) {
                        TabSwitcherScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.BOOKMARKS) {
                        BookmarksScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url -> viewModel.openUrlInActiveTab(url) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url -> viewModel.openUrlInActiveTab(url) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onOpenPrivacySettings = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                            onOpenDataClearing = { navController.navigate(Routes.DATA_CLEARING_SETTINGS) },
                            onOpenAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.PRIVACY_SETTINGS) {
                        PrivacySettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.DATA_CLEARING_SETTINGS) {
                        DataClearingSettingsScreen(
                            viewModel = viewModel,
                            onOpenFireproofSites = { navController.navigate(Routes.FIREPROOF_SITES) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.FIREPROOF_SITES) {
                        FireproofSitesScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.APPEARANCE_SETTINGS) {
                        AppearanceSettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.clearDataIfAutoClearEnabled()
        }
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val url = intent.dataString
            if (!url.isNullOrBlank()) {
                viewModel.openUrlInNewTab(url)
            }
        }
    }
}
