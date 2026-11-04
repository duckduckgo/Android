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

package com.duckduckgo.pir.impl.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.pir.api.PirScreens.PirDashboardWebViewScreen
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command.SendJsEvent
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command.SendResponseToJs
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirDashboardFreeScanEntryPointHolder
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebViewClient
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute.NativePurchaseFlow
import com.duckduckgo.pir.impl.databinding.ActivityPirDashboardWebviewBinding
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirNotificationPromptType
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDashboardWebViewScreen::class)
@ContributeToActivityStarter(PirFreemiumDashboardScreens.FromEntryPoint::class)
@ContributeToActivityStarter(PirFreemiumDashboardScreens.FromRemoteMessage::class, screenName = "pir.main")
class PirDashboardWebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var webViewClient: PirDashboardWebViewClient

    @Named("PirDashboardWebMessaging")
    @Inject
    lateinit var pirWebJsMessaging: JsMessaging

    @Inject
    lateinit var pirNotificationManager: PirNotificationManager

    @Inject
    lateinit var pirDashboardUrlProvider: PirDashboardUrlProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var freeScanEntryPointHolder: PirDashboardFreeScanEntryPointHolder

    private val viewModel: PirDashboardWebViewViewModel by bindViewModel()

    private val binding: ActivityPirDashboardWebviewBinding by viewBinding()

    private val notificationPermissionLauncher = registerForActivityResult(RequestPermission()) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupCloseToolbarIfNeeded()
        configureEdgeToEdgeInsets()

        // set before the web view loads, so the handshake always waits on this visit's entry point
        freeScanEntryPointHolder.freeScanEntryPoint = viewModel.freeScanEntryPoint
        setupWebView()
        observeCommands()

        pirNotificationManager.createNotificationChannel()
        lifecycle.addObserver(viewModel)

        // avoid counting relaunch from Recents as fresh launch
        val launchedFromRecents = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        viewModel.onLaunched(
            isRestored = savedInstanceState != null || launchedFromRecents,
            fromFreemiumScanCompleteNotification = intent.getBooleanExtra(EXTRA_FROM_FREEMIUM_SCAN_COMPLETE_NOTIFICATION, false),
            freemiumLaunch = freemiumLaunch(savedInstanceState),
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed(notificationPermissionGranted = isNotificationPermissionGranted())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.resolvedFreeScanEntryPoint?.let { outState.putString(KEY_FREE_SCAN_ENTRY_POINT, it.name) }
    }

    /**
     * After process death the ViewModel is new, so a restored dashboard uses the entry point it saved rather than resolving
     * the launch again: a scan that finished in the meantime would otherwise turn a free scan banner visit into a results one.
     */
    private fun freemiumLaunch(savedInstanceState: Bundle?): PirFreemiumDashboardScreens? {
        if (savedInstanceState == null) {
            return intent.getActivityParams(PirFreemiumDashboardScreens::class.java)
        }

        return savedInstanceState.getString(KEY_FREE_SCAN_ENTRY_POINT)
            ?.let { name -> PirFreeScanEntryPoint.entries.firstOrNull { it.name == name } }
            ?.let { PirFreemiumDashboardScreens.FromEntryPoint(it) }
    }

    override fun onDestroy() {
        cleanupWebView()
        super.onDestroy()
    }

    private fun setupCloseToolbarIfNeeded() {
        if (!intent.getBooleanExtra(EXTRA_SHOW_CLOSE_BUTTON, false)) return

        binding.includeToolbar.appBarLayout.isVisible = true
        setupToolbar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        supportActionBar?.title = ""

        val toolbarColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorToolbar)
        binding.includeToolbar.appBarLayout.setBackgroundColor(toolbarColor)
        binding.includeToolbar.toolbar.setBackgroundColor(toolbarColor)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyStatusBarAndHorizontalInsets(binding.root)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.webViewContainer, drawBehindGestureNav = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        pirWebJsMessaging.register(
            binding.pirWebView,
            object : JsMessageCallback() {

                override fun process(
                    featureName: String,
                    method: String,
                    id: String?,
                    data: JSONObject?,
                ) {
                    viewModel.handleJsMessage(
                        featureName = featureName,
                        method = method,
                        id = id,
                        data = data,
                    )
                }
            },
        )

        binding.pirWebView.webViewClient = webViewClient
        webViewClient.listener = object : PirDashboardWebViewClient.Listener {
            override fun onSubscriptionPurchaseRequested(route: NativePurchaseFlow) {
                viewModel.onSubscriptionPurchaseRequested(route)
            }
        }
        binding.pirWebView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                message: android.os.Message,
            ): Boolean {
                val transport = message.obj as WebView.WebViewTransport
                transport.webView = binding.pirWebView
                message.sendToTarget()
                return true
            }
        }

        binding.pirWebView.settings.apply {
            userAgentString = PirDashboardWebConstants.CUSTOM_UA
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            setSupportZoom(true)
        }

        binding.pirWebView.loadUrl(pirDashboardUrlProvider.getUrl())
    }

    private fun cleanupWebView() {
        webViewClient.listener = null
        binding.pirWebView.stopLoading()
        binding.pirWebView.removeJavascriptInterface(pirWebJsMessaging.context)
        binding.webViewContainer.removeView(binding.pirWebView)
        binding.pirWebView.destroy()
    }

    private fun observeCommands() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is SendJsEvent -> sendJsEvent(command.event)
            is SendResponseToJs -> sendResponseToJs(command.data)
            is Command.ShowManualConfigWarning -> {
                binding.manualConfigWarning.isVisible = command.show
            }

            Command.ReloadWebView -> binding.pirWebView.reload()

            is Command.LaunchSubscriptionPurchase -> globalActivityStarter.start(
                this,
                SubscriptionPurchase(origin = command.origin, featurePage = command.featurePage),
            )

            Command.CheckNotificationPermission -> checkNotificationPermission()
            is Command.ShowNotificationPermissionPrompt -> showNotificationPermissionPrompt(command.promptType)
            Command.RequestNotificationPermission -> requestNotificationPermission()
            Command.OpenNotificationSettings -> openNotificationSettings()
        }
    }

    private fun checkNotificationPermission() {
        val isAtLeastApi33 = Build.VERSION.SDK_INT >= 33
        viewModel.onNotificationPermissionStateComputed(
            isAtLeastApi33 = isAtLeastApi33,
            permissionGranted = isAtLeastApi33 && isNotificationPermissionGranted(),
            shouldShowRationale = isAtLeastApi33 &&
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun isNotificationPermissionGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED

    private fun showNotificationPermissionPrompt(promptType: PirNotificationPromptType) {
        val (messageRes, positiveButtonRes) = when (promptType) {
            PirNotificationPromptType.REQUEST_PERMISSION ->
                R.string.pirNotificationPermissionPromptMessage to R.string.pirNotificationPermissionPromptAllow
            PirNotificationPromptType.OPEN_SETTINGS ->
                R.string.pirNotificationPermissionPromptSettingsMessage to R.string.pirNotificationPermissionPromptOpenSettings
        }

        TextAlertDialogBuilder(this)
            .setTitle(R.string.pirNotificationPermissionPromptTitle)
            .setMessage(messageRes)
            .setPositiveButton(positiveButtonRes)
            .setNegativeButton(R.string.pirNotificationPermissionPromptNotNow)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onDialogShown() {
                        viewModel.onNotificationPermissionPromptShown(promptType)
                    }

                    override fun onPositiveButtonClicked() {
                        viewModel.onNotificationPermissionPromptAccepted(promptType)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onNotificationPermissionPromptDismissed(promptType)
                    }

                    override fun onDialogCancelled() {
                        viewModel.onNotificationPermissionPromptDismissed(promptType)
                    }
                },
            )
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(settingsIntent)
    }

    private fun sendJsEvent(event: SubscriptionEventData) {
        pirWebJsMessaging.sendSubscriptionEvent(event)
    }

    private fun sendResponseToJs(data: JsCallbackData) {
        pirWebJsMessaging.onResponse(data)
    }

    companion object {
        const val EXTRA_SHOW_CLOSE_BUTTON = "extra_show_close_button"
        const val EXTRA_FROM_FREEMIUM_SCAN_COMPLETE_NOTIFICATION = "extra_from_freemium_scan_complete_notification"
        private const val KEY_FREE_SCAN_ENTRY_POINT = "key_free_scan_entry_point"
    }
}
