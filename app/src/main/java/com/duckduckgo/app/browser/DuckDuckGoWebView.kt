/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Message
import android.print.PrintDocumentAdapter
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.compareSemanticVersion
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * WebView subclass which allows the WebView to
 *   - hide the toolbar when placed in a CoordinatorLayout
 *   - add the flag so that users' typing isn't used for personalisation
 *
 * Originally based on https://github.com/takahirom/webview-in-coordinatorlayout for scrolling behaviour
 */
class DuckDuckGoWebView : WebView, NestedScrollingChild3 {
    private var lastClampedTopY: Boolean = true // when created we are always at the top
    private var contentAllowsSwipeToRefresh: Boolean = true
    private var enableSwipeRefreshCallback: ((Boolean) -> Unit)? = null
    private var hasGestureFinished = true
    private var canSwipeToRefresh = true

    private var lastY: Int = 0
    private var lastDeltaY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0
    private var nestedScrollHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private val helper = CoordinatorLayoutHelper()

    private var isDestroyed: Boolean = false
    var isSafeWebViewEnabled: Boolean = false

    private val javaScriptBridge = JavaScriptBridge(this)

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : super(context, attrs) {
        isNestedScrollingEnabled = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        helper.onViewAttached(this)
    }

    override fun destroy() {
        isDestroyed = true && isSafeWebViewEnabled
        super.destroy()
    }

    override fun stopLoading() {
        if (!isDestroyed) {
            super.stopLoading()
        }
    }

    override fun onPause() {
        if (!isDestroyed) {
            super.onPause()
        }
    }

    override fun onResume() {
        if (!isDestroyed) {
            super.onResume()
        }
    }

    override fun loadUrl(url: String) {
        if (!isDestroyed) {
            super.loadUrl(url)
        }
    }

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        if (!isDestroyed) {
            super.loadUrl(url, additionalHttpHeaders)
        }
    }

    override fun reload() {
        if (!isDestroyed) {
            super.reload()
        }
    }

    override fun goForward() {
        if (!isDestroyed) {
            super.goForward()
        }
    }

    override fun goBackOrForward(steps: Int) {
        if (!isDestroyed) {
            super.goBackOrForward(steps)
        }
    }

    override fun findAllAsync(find: String) {
        if (!isDestroyed) {
            super.findAllAsync(find)
        }
    }

    override fun findNext(forward: Boolean) {
        if (!isDestroyed) {
            super.findNext(forward)
        }
    }

    override fun clearSslPreferences() {
        if (!isDestroyed) {
            super.clearSslPreferences()
        }
    }

    override fun setDownloadListener(listener: DownloadListener?) {
        if (!isDestroyed) {
            super.setDownloadListener(listener)
        }
    }

    override fun requestFocusNodeHref(hrefMsg: Message?) {
        if (!isDestroyed) {
            super.requestFocusNodeHref(hrefMsg)
        }
    }

    override fun setWebChromeClient(client: WebChromeClient?) {
        if (!isDestroyed) {
            super.setWebChromeClient(client)
        }
    }

    override fun setWebViewClient(client: WebViewClient) {
        if (!isDestroyed) {
            super.setWebViewClient(client)
        }
    }

    override fun setFindListener(listener: FindListener?) {
        if (!isDestroyed) {
            super.setFindListener(listener)
        }
    }

    override fun getUrl(): String? {
        if (isDestroyed) return null
        return super.getUrl()
    }

    fun safeCopyBackForwardList(): WebBackForwardList? {
        if (isDestroyed) return null
        return (this as WebView).safeCopyBackForwardList()
    }

    fun createSafePrintDocumentAdapter(documentName: String): PrintDocumentAdapter? {
        if (isDestroyed) return null
        return createPrintDocumentAdapter(documentName)
    }

    val safeSettings: WebSettings?
        get() {
            if (isDestroyed) return null
            return getSettings()
        }

    val safeHitTestResult: HitTestResult?
        get() {
            if (isDestroyed) return null
            return getHitTestResult()
        }

    fun setBottomMatchingBehaviourEnabled(value: Boolean) {
        helper.setBottomMatchingBehaviourEnabled(value)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs) ?: return null

        addNoPersonalisedFlag(outAttrs)

        return inputConnection
    }

    suspend fun isScrollingBlocked(width: Int, height: Int): Boolean = suspendCoroutine { cont ->
        javaScriptBridge.evaluateJavascript(SCROLLING_BLOCKED_JS.format(width, height), cont)
    }

    private fun addNoPersonalisedFlag(outAttrs: EditorInfo) {
        outAttrs.imeOptions = outAttrs.imeOptions or IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        var returnValue = false

        val event = MotionEvent.obtain(ev)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_UP -> {
                hasGestureFinished = true
                returnValue = super.onTouchEvent(event)
                stopNestedScroll()
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY

                lastClampedTopY = deltaY <= 0

                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    lastY = eventY - scrollOffset[1]
                    event.offsetLocation(0f, (-scrollOffset[1]).toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                returnValue = super.onTouchEvent(event)

                if (dispatchNestedScroll(0, scrollOffset[1], 0, deltaY, scrollOffset)) {
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }

                lastDeltaY = deltaY
            }

            MotionEvent.ACTION_DOWN -> {
                hasGestureFinished = false
                // disable swipeRefresh until we can be sure it should be enabled
                enableSwipeRefresh(false)

                returnValue = super.onTouchEvent(event)
                lastY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }

            else -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll()
            }
        }

        return returnValue
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        nestedScrollHelper.isNestedScrollingEnabled = enabled
    }

    override fun stopNestedScroll() {
        nestedScrollHelper.stopNestedScroll()
    }

    override fun isNestedScrollingEnabled(): Boolean = nestedScrollHelper.isNestedScrollingEnabled
    override fun startNestedScroll(
        axes: Int,
        type: Int,
    ): Boolean = nestedScrollHelper.startNestedScroll(axes)

    override fun startNestedScroll(axes: Int): Boolean = nestedScrollHelper.startNestedScroll(axes)
    override fun stopNestedScroll(type: Int) {
        nestedScrollHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean = nestedScrollHelper.hasNestedScrollingParent()
    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        nestedScrollHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean = nestedScrollHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = nestedScrollHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean =
        nestedScrollHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = nestedScrollHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean =
        nestedScrollHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean =
        nestedScrollHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(
        velocityX: Float,
        velocityY: Float,
    ): Boolean =
        nestedScrollHelper.dispatchNestedPreFling(velocityX, velocityY)

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        clampedX: Boolean,
        clampedY: Boolean,
    ) {
        // taking into account lastDeltaY since we are only interested whether we clamped at the top
        lastClampedTopY = clampedY && lastDeltaY <= 0

        if (!lastClampedTopY) {
            canSwipeToRefresh = false // disable because user scrolled down so we need a new gesture
        }

        if (lastClampedTopY && hasGestureFinished) {
            canSwipeToRefresh = true // only enable if at the top and gestured finished
        }

        enableSwipeRefresh(canSwipeToRefresh && clampedY && scrollY == 0 && (lastDeltaY <= 0 || nestedOffsetY == 0))
        post(helper::computeBottomMarginIfNeeded)
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    fun setEnableSwipeRefreshCallback(callback: (Boolean) -> Unit) {
        enableSwipeRefreshCallback = callback
    }

    fun removeEnableSwipeRefreshCallback() {
        enableSwipeRefreshCallback = null
    }

    private fun enableSwipeRefresh(enable: Boolean) {
        enableSwipeRefreshCallback?.invoke(enable && contentAllowsSwipeToRefresh)
    }

    private fun setContentAllowsSwipeToRefresh(allowed: Boolean) {
        contentAllowsSwipeToRefresh = allowed
        if (!allowed) {
            enableSwipeRefresh(false)
        }
    }

    suspend fun isWebMessageListenerSupported(
        dispatchers: DispatcherProvider,
        webViewVersionProvider: WebViewVersionProvider,
    ): Boolean {
        return withContext(dispatchers.io()) {
            webViewVersionProvider.getFullVersion()
                .compareSemanticVersion(WEB_MESSAGE_LISTENER_WEBVIEW_VERSION)?.let { it >= 0 } ?: false
        } && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    @SuppressLint("RequiresFeature", "AddWebMessageListenerUsage")
    suspend fun safeAddWebMessageListener(
        dispatchers: DispatcherProvider,
        webViewVersionProvider: WebViewVersionProvider,
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebMessageListener,
    ): Boolean = runCatching {
        if (isWebMessageListenerSupported(dispatchers, webViewVersionProvider) && !isDestroyed) {
            WebViewCompat.addWebMessageListener(
                this,
                jsObjectName,
                allowedOriginRules,
                listener,
            )
            true
        } else {
            false
        }
    }.getOrElse { exception ->
        Timber.e(exception, "Error adding WebMessageListener: $jsObjectName")
        false
    }

    @SuppressLint("RequiresFeature", "RemoveWebMessageListenerUsage")
    suspend fun safeRemoveWebMessageListener(
        dispatchers: DispatcherProvider,
        webViewVersionProvider: WebViewVersionProvider,
        jsObjectName: String,
    ): Boolean = runCatching {
        if (isWebMessageListenerSupported(dispatchers, webViewVersionProvider) && !isDestroyed) {
            WebViewCompat.removeWebMessageListener(
                this,
                jsObjectName,
            )
            true
        } else {
            false
        }
    }.getOrElse { exception ->
        Timber.e(exception, "Error removing WebMessageListener: $jsObjectName")
        false
    }

    companion object {

        /*
         * Taken from EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
         * We can't use that value directly as it was only added on Oreo, but we can apply the value anyway.
         */
        private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
        private const val WEB_MESSAGE_LISTENER_WEBVIEW_VERSION = "126.0.6478.40"

        private const val JS_BRIDGE_NAME = "bridge"

        // This JS code will attempt to check if the scrolling is blocked
        private const val SCROLLING_BLOCKED_JS = """
            (async function(rectWidth, rectHeight) {
                // Check if the body or html has overflow set to scroll or auto
                const bodyOverflow = window.getComputedStyle(document.body).overflowY;
                const htmlOverflow = window.getComputedStyle(document.documentElement).overflowY;
            
                // Check the height of the document and the viewport
                const documentHeight = Math.max(
                    document.body.scrollHeight,
                    document.documentElement.scrollHeight,
                    document.body.offsetHeight,
                    document.documentElement.offsetHeight,
                    document.body.clientHeight,
                    document.documentElement.clientHeight
                );
            
                const viewportHeight = window.innerHeight;
            
                // Determine if the page is scrollable
                const isScrollable = (bodyOverflow === 'scroll' || bodyOverflow === 'auto' || bodyOverflow === 'visible' ||
                                     htmlOverflow === 'scroll' || htmlOverflow === 'auto' || htmlOverflow === 'visible') &&
                                     (documentHeight > viewportHeight);
            
                // Function to check for fixed position elements behind the rectangle
                async function checkForFixedElements() {
                    return new Promise((resolve) => {
                        const observer = new IntersectionObserver((entries) => {
                            entries.forEach(entry => {
                                const isIntersecting = entry.isIntersecting;
                                const isFixedPosition = window.getComputedStyle(entry.target).position === 'fixed';
                                const hasWidth = entry.target.offsetWidth > 0;
                                const hasHeight = entry.target.offsetHeight > 0;
                                const isVisibleStyle = window.getComputedStyle(entry.target).visibility !== 'hidden';
                                const isDisplayNone = window.getComputedStyle(entry.target).display === 'none';
            
                                // Combine conditions to determine visibility
                                const isVisible = isIntersecting && isFixedPosition && hasWidth && hasHeight && isVisibleStyle && !isDisplayNone;
            
                                if (isVisible) {
                                    resolve(true);
                                }
                            });
                            resolve(false);
                        }, {
                            root: null,
                            rootMargin: '0px',
                            threshold: 0
                        });
            
                        // Create a rectangle element at the bottom of the viewport
                        const rect = document.createElement('div');
                        rect.style.position = 'absolute';
                        rect.style.bottom = '0';
                        rect.style.left = '0';
                        rect.style.width = rectWidth + 'px';
                        rect.style.height = rectHeight + 'px';
                        rect.style.pointerEvents = 'none'; // Make sure it doesn't block interactions
                        document.body.appendChild(rect);
            
                        // Observe all elements with position: fixed
                        const fixedElements = document.querySelectorAll('*');
                        fixedElements.forEach(el => {
                            const style = window.getComputedStyle(el);
                            if (style.position === 'fixed') {
                                observer.observe(el);
                            }
                        });
            
                        // Clean up after a short delay
                        setTimeout(() => {
                            observer.disconnect();
                            document.body.removeChild(rect);
                            resolve(false); // Resolve false if no fixed elements were found
                        }, 1000);
                    });
                }
            
                // Call the function and return the result
                if (!isScrollable) {
                    return true; // scrolling blocked
                } else {
                    const fixedBehind = await checkForFixedElements();
                    const result = !isScrollable || fixedBehind;
                    return result;
                }
            })(%d, %d).then(result => $JS_BRIDGE_NAME.receiveJsResult(result));
        """
    }

    /**
     * This class is used to communicate between async JavaScript executed inside the WebView and the Android code
     */
    internal class JavaScriptBridge(private val webView: DuckDuckGoWebView) {
        private var continuation: Continuation<Boolean>? = null

        init {
            webView.addJavascriptInterface(this, JS_BRIDGE_NAME)
        }

        fun evaluateJavascript(js: String, continuation: Continuation<Boolean>) {
            this.continuation = continuation
            webView.evaluateJavascript(js, null)
        }

        @JavascriptInterface
        fun receiveJsResult(result: String) {
            continuation?.resume(result.toBooleanStrictOrNull() ?: false)
        }
    }
}
