/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.widget

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.pixels.AppPixelName.SHORTCUTS_WIDGET_ADDED
import com.duckduckgo.app.pixels.AppPixelName.SHORTCUTS_WIDGET_DELETED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

class ShortcutsWidget : AppWidgetProvider() {

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var networkProtectionState: NetworkProtectionState

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var searchWidgetLifecycleDelegate: SearchWidgetLifecycleDelegate

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        inject(context)
        when (intent?.action) {
            ACTION_GENERATE_EMAIL -> handleGenerateEmail(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        appCoroutineScope.launch {
            searchWidgetLifecycleDelegate.handleOnWidgetEnabled(SHORTCUTS_WIDGET_ADDED)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        logcat(INFO) { "ShortcutsWidget - onUpdate" }
        appCoroutineScope.launch {
            appWidgetIds.forEach { id ->
                updateWidget(context, appWidgetManager, id)
            }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        searchWidgetLifecycleDelegate.handleOnWidgetDisabled(SHORTCUTS_WIDGET_DELETED)
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.shortcuts_widget_daynight)

        // Duck.ai shortcut
        val duckAiIntent = BrowserActivity.intent(context, openDuckChat = true).also { it.action = Intent.ACTION_VIEW }
        val duckAiStackBuilder = TaskStackBuilder.create(context)
            .addNextIntent(duckAiIntent)
        val duckAiPendingIntent = duckAiStackBuilder.getPendingIntent(
            REQUEST_CODE_DUCK_AI,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        remoteViews.setOnClickPendingIntent(R.id.shortcutDuckAi, duckAiPendingIntent)

        // Search shortcut
        val searchIntent = SystemSearchActivity.fromWidget(context)
        val searchPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SEARCH,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        remoteViews.setOnClickPendingIntent(R.id.shortcutSearch, searchPendingIntent)

        // Email shortcut
        val emailIntent = Intent(context, ShortcutsWidget::class.java).apply {
            action = ACTION_GENERATE_EMAIL
        }
        val emailPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_EMAIL,
            emailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        remoteViews.setOnClickPendingIntent(R.id.shortcutEmail, emailPendingIntent)

        // VPN shortcut
        val vpnIntent = globalActivityStarter.startIntent(context, NetworkProtectionManagementScreenNoParams)
        if (vpnIntent != null) {
            vpnIntent.action = Intent.ACTION_VIEW
            val vpnStackBuilder = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(vpnIntent)
            val vpnPendingIntent = vpnStackBuilder.getPendingIntent(
                REQUEST_CODE_VPN,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            remoteViews.setOnClickPendingIntent(R.id.shortcutVpn, vpnPendingIntent)
        }

        // Update VPN status indicator
        val vpnEnabled = networkProtectionState.isRunning()
        remoteViews.setViewVisibility(
            R.id.vpnStatusIndicator,
            if (vpnEnabled) View.VISIBLE else View.GONE,
        )

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun handleGenerateEmail(context: Context) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!emailManager.isSignedIn()) {
                // Open Email Protection setup in browser
                val setupIntent = BrowserActivity.intent(context, queryExtra = EMAIL_PROTECTION_URL).also {
                    it.action = Intent.ACTION_VIEW
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(setupIntent)
            } else {
                val alias = emailManager.getAlias()
                if (alias != null) {
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(CLIPBOARD_LABEL, alias)
                    clipboardManager.setPrimaryClip(clip)

                    launch(dispatchers.main()) {
                        Toast.makeText(context, R.string.emailAliasCopied, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    launch(dispatchers.main()) {
                        Toast.makeText(context, R.string.emailAliasError, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    companion object {
        private const val ACTION_GENERATE_EMAIL = "com.duckduckgo.widget.ACTION_GENERATE_EMAIL"
        private const val EMAIL_PROTECTION_URL = "https://duckduckgo.com/email/"
        private const val CLIPBOARD_LABEL = "Duck Address"

        private const val REQUEST_CODE_DUCK_AI = 1570
        private const val REQUEST_CODE_SEARCH = 1571
        private const val REQUEST_CODE_EMAIL = 1572
        private const val REQUEST_CODE_VPN = 1573

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ShortcutsWidget::class.java),
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, ShortcutsWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
