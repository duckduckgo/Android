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
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * WebView subclass which allows the WebView to
 *   - hide the toolbar when placed in a CoordinatorLayout
 *   - add the flag so that users' typing isn't used for personalisation
 *
 * Originally based on https://github.com/takahirom/webview-in-coordinatorlayout for scrolling behaviour
 */
@InjectWith(ViewScope::class)
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

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : super(context, attrs) {
        isNestedScrollingEnabled = true
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
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

    private fun addNoPersonalisedFlag(outAttrs: EditorInfo) {
        outAttrs.imeOptions = outAttrs.imeOptions or IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)

        val returnValue: Boolean

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

    fun isDestroyed(): Boolean {
        return isDestroyed
    }

    @SuppressLint("RequiresFeature", "AddWebMessageListenerUsage")
    suspend fun safeAddWebMessageListener(
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebMessageListener,
    ) = runCatching {
        if (!isDestroyed) {
            if (::dispatcherProvider.isInitialized) {
                withContext(dispatcherProvider.main()) {
                    WebViewCompat.addWebMessageListener(
                        this@DuckDuckGoWebView,
                        jsObjectName,
                        allowedOriginRules,
                        listener,
                    )
                }
            }
        }
    }.getOrElse { exception ->
        logcat(ERROR) { "Error adding WebMessageListener: $jsObjectName: ${exception.asLog()}" }
    }

    @SuppressLint("RequiresFeature", "RemoveWebMessageListenerUsage")
    suspend fun safeRemoveWebMessageListener(
        jsObjectName: String,
    ) = runCatching {
        if (!isDestroyed) {
            if (::dispatcherProvider.isInitialized) {
                withContext(dispatcherProvider.main()) {
                    WebViewCompat.removeWebMessageListener(
                        this@DuckDuckGoWebView,
                        jsObjectName,
                    )
                }
            }
        }
    }.getOrElse { exception ->
        logcat(ERROR) { "Error removing WebMessageListener: $jsObjectName: ${exception.asLog()}" }
    }

    @SuppressLint("RequiresFeature", "AddDocumentStartJavaScriptUsage")
    suspend fun safeAddDocumentStartJavaScript(
        script: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandler? {
        return runCatching {
            if (!isDestroyed) {
                if (::dispatcherProvider.isInitialized) {
                    return withContext(dispatcherProvider.main()) {
                        return@withContext WebViewCompat.addDocumentStartJavaScript(
                            this@DuckDuckGoWebView,
                            script,
                            allowedOriginRules,
                        )
                    }
                }
            }
            null
        }.getOrElse { e ->
            logcat(ERROR) { "Error calling addDocumentStartJavaScript: ${e.asLog()}" }
            null
        }
    }

    companion object {

        /*
         * Taken from EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
         * We can't use that value directly as it was only added on Oreo, but we can apply the value anyway.
         */
        private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    }
}
