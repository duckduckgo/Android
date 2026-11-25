package com.example.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.BlockedTracker
import com.example.model.BrowserSettings
import com.example.model.BrowserTab
import com.example.privacy.PrivacyEngine
import com.example.viewmodel.WebViewCommand
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayInputStream

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@Composable
fun WebViewHost(
    activeTab: BrowserTab,
    settings: BrowserSettings,
    isPrivacyActiveForUrl: (String?) -> Boolean,
    webCommands: SharedFlow<WebViewCommand>,
    onPageStarted: (tabId: String, url: String) -> Unit,
    onPageProgress: (tabId: String, progress: Int) -> Unit,
    onPageFinished: (tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onTrackerBlocked: (tabId: String, tracker: BlockedTracker) -> Unit,
    onFindMatchesUpdated: (activeOrdinal: Int, count: Int) -> Unit,
    onDownloadStarted: (fileName: String, url: String, mimeType: String, sizeBytes: Long, systemId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webViewPool = remember { mutableMapOf<String, WebView>() }

    DisposableEffect(Unit) {
        onDispose {
            webViewPool.values.forEach { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViewPool.clear()
        }
    }

    LaunchedEffect(webCommands) {
        webCommands.collect { cmd ->
            when (cmd) {
                is WebViewCommand.LoadUrl -> {
                    val wv = webViewPool[cmd.tabId]
                    if (wv != null) {
                        val headers = if (cmd.gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()
                        wv.loadUrl(cmd.url, headers)
                    }
                }
                is WebViewCommand.GoBack -> webViewPool[cmd.tabId]?.let { if (it.canGoBack()) it.goBack() }
                is WebViewCommand.GoForward -> webViewPool[cmd.tabId]?.let { if (it.canGoForward()) it.goForward() }
                is WebViewCommand.Reload -> webViewPool[cmd.tabId]?.reload()
                is WebViewCommand.StopLoading -> webViewPool[cmd.tabId]?.stopLoading()
                is WebViewCommand.SetDesktopMode -> {
                    webViewPool[cmd.tabId]?.let { wv ->
                        wv.settings.userAgentString = if (cmd.enabled) DESKTOP_USER_AGENT else null
                        wv.settings.useWideViewPort = true
                        wv.settings.loadWithOverviewMode = true
                        wv.reload()
                    }
                }
                is WebViewCommand.FindInPage -> webViewPool[cmd.tabId]?.findAllAsync(cmd.query)
                is WebViewCommand.FindNext -> webViewPool[cmd.tabId]?.findNext(cmd.forward)
                is WebViewCommand.ClearFindInPage -> webViewPool[cmd.tabId]?.clearMatches()
                is WebViewCommand.PrintPage -> {
                    webViewPool[cmd.tabId]?.let { wv ->
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        val jobName = wv.title?.ifBlank { "DuckDuckGo Page" } ?: "DuckDuckGo Page"
                        printManager?.print(
                            jobName,
                            wv.createPrintDocumentAdapter(jobName),
                            PrintAttributes.Builder().build(),
                        )
                    }
                }
                is WebViewCommand.DisposeTab -> {
                    webViewPool.remove(cmd.tabId)?.let { wv ->
                        wv.stopLoading()
                        wv.destroy()
                    }
                }
                WebViewCommand.DisposeAllTabs -> {
                    webViewPool.values.forEach { wv ->
                        wv.stopLoading()
                        wv.destroy()
                    }
                    webViewPool.clear()
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            createConfiguredWebView(
                context = ctx,
                tab = activeTab,
                settingsProvider = { settings },
                isPrivacyActiveForUrl = isPrivacyActiveForUrl,
                onPageStarted = onPageStarted,
                onPageProgress = onPageProgress,
                onPageFinished = onPageFinished,
                onTrackerBlocked = onTrackerBlocked,
                onFindMatchesUpdated = onFindMatchesUpdated,
                onDownloadStarted = onDownloadStarted,
            ).also { wv ->
                webViewPool[activeTab.id] = wv
                if (activeTab.url.isNotBlank()) {
                    val headers = if (settings.gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()
                    wv.loadUrl(activeTab.url, headers)
                }
            }
        },
        update = { currentView ->
            // Switch active WebView if the tab changed
            val targetWebView = webViewPool.getOrPut(activeTab.id) {
                createConfiguredWebView(
                    context = context,
                    tab = activeTab,
                    settingsProvider = { settings },
                    isPrivacyActiveForUrl = isPrivacyActiveForUrl,
                    onPageStarted = onPageStarted,
                    onPageProgress = onPageProgress,
                    onPageFinished = onPageFinished,
                    onTrackerBlocked = onTrackerBlocked,
                    onFindMatchesUpdated = onFindMatchesUpdated,
                    onDownloadStarted = onDownloadStarted,
                ).also { wv ->
                    if (activeTab.url.isNotBlank()) {
                        val headers = if (settings.gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()
                        wv.loadUrl(activeTab.url, headers)
                    }
                }
            }
            if (currentView !== targetWebView) {
                val parent = currentView.parent as? ViewGroup
                if (parent != null) {
                    val index = parent.indexOfChild(currentView)
                    parent.removeView(currentView)
                    (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                    parent.addView(targetWebView, index, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                }
            } else if (activeTab.url.isNotBlank() && targetWebView.url.isNullOrBlank()) {
                val headers = if (settings.gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()
                targetWebView.loadUrl(activeTab.url, headers)
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    tab: BrowserTab,
    settingsProvider: () -> BrowserSettings,
    isPrivacyActiveForUrl: (String?) -> Boolean,
    onPageStarted: (tabId: String, url: String) -> Unit,
    onPageProgress: (tabId: String, progress: Int) -> Unit,
    onPageFinished: (tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onTrackerBlocked: (tabId: String, tracker: BlockedTracker) -> Unit,
    onFindMatchesUpdated: (activeOrdinal: Int, count: Int) -> Unit,
    onDownloadStarted: (fileName: String, url: String, mimeType: String, sizeBytes: Long, systemId: Long) -> Unit,
): WebView {
    val tabId = tab.id
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !tab.isFireTab
            databaseEnabled = !tab.isFireTab
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (tab.isDesktopMode) {
                userAgentString = DESKTOP_USER_AGENT
            }
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onFindMatchesUpdated(activeMatchOrdinal, numberOfMatches)
            }
        }

        setDownloadListener(
            DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val downloadId = runCatching {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        setTitle(fileName)
                        setDescription("Downloading via DuckDuckGo")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                }.getOrDefault(-1L)

                onDownloadStarted(fileName, url, mimetype ?: "application/octet-stream", contentLength.coerceAtLeast(0L), downloadId)
            },
        )

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onPageProgress(tabId, newProgress)
            }
        }

        webViewClient = object : WebViewClient() {
            @Volatile
            private var currentPageUrl: String? = tab.url

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val safeUrl = url ?: return
                currentPageUrl = safeUrl
                onPageStarted(tabId, safeUrl)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val safeUrl = url ?: currentPageUrl ?: return
                val title = view?.title.orEmpty()
                onPageFinished(
                    tabId,
                    safeUrl,
                    title,
                    view?.canGoBack() ?: false,
                    view?.canGoForward() ?: false,
                )

                val currentSettings = settingsProvider()
                if (currentSettings.gpcEnabled) {
                    view?.evaluateJavascript(
                        "try{Object.defineProperty(navigator,'globalPrivacyControl',{value:true,configurable:true});}catch(e){}",
                        null,
                    )
                }
                if (currentSettings.youtubeAdBlockingEnabled && safeUrl.contains("youtube.com", ignoreCase = true)) {
                    view?.evaluateJavascript(
                        """
                        (function(){
                          const style = document.getElementById('ddg-yt-clean') || document.createElement('style');
                          style.id = 'ddg-yt-clean';
                          style.textContent = 'ytd-ad-slot-renderer, .ytp-ad-module, .ad-showing .video-ads { display: none !important; }';
                          document.head && document.head.appendChild(style);
                        })();
                        """.trimIndent(),
                        null,
                    )
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https") {
                    val raw = uri.toString()
                    val currentSettings = settingsProvider()
                    val stripped = if (currentSettings.stripTrackingParamsEnabled) {
                        PrivacyEngine.stripTrackingParameters(raw)
                    } else {
                        raw
                    }
                    if (stripped != raw) {
                        val headers = if (currentSettings.gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()
                        view?.loadUrl(stripped, headers)
                        return true
                    }
                    return false
                }
                return runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }.getOrDefault(true)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (!isPrivacyActiveForUrl(currentPageUrl)) return null
                val blocked = PrivacyEngine.matchTracker(url, currentPageUrl) ?: return null
                onTrackerBlocked(tabId, blocked)
                return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
    }
}
