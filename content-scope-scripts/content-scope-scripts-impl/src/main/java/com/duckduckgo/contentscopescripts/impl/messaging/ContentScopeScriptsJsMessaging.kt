/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.ContentScopeScriptsFeature
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.messaging.ContentScopeScriptsMessagingPixelName.INBOUND_QUEUE_FULL
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsErrorDetails
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named

// Backstop against unbounded growth, not a tuning parameter: observed depth on real pages is 0-1.
internal const val MAX_QUEUED_MESSAGES = 512

@ContributesBinding(ActivityScope::class)
@Named("ContentScopeScripts")
class ContentScopeScriptsJsMessaging @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
    private val dispatcherProvider: DispatcherProvider,
    private val coreContentScopeScripts: CoreContentScopeScripts,
    private val handlers: PluginPoint<ContentScopeJsMessageHandlersPlugin>,
    private val contentScopeScriptsFeature: ContentScopeScriptsFeature,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : JsMessaging {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    /**
     * The WebView and callback a message must be routed against, published as one object so a reader gets a consistent
     * pair rather than the WebView of one registration and the callback of the next. Queued messages carry the instance
     * they arrived under, which is what lets [handle] recognise a message belonging to a WebView that is already gone —
     * draining the queue in [register] cannot do that on its own, because the consumer may already have taken a message
     * out of it.
     */
    private class Registration(
        val webView: WebView,
        val jsMessageCallback: JsMessageCallback,
    )

    @Volatile
    private var registration: Registration? = null

    /**
     * The producer/consumer handoff for inbound messages. [process] is the producer: it runs on the WebView JavaBridge
     * thread and only enqueues. [ensureQueueConsumerRunning] starts the consumer, of which there is at most one, so that
     * handlers keep seeing messages one at a time and in the order the page sent them. The consumer stops once the queue
     * drains, which keeps it from outliving the WebView.
     *
     * Bounded because enqueueing removed the backpressure the synchronous JavaBridge call used to provide: the page's JS
     * thread no longer waits for a message to be handled, so nothing else limits how many can pile up behind a slow
     * handler.
     */
    private val inboundQueue = LinkedBlockingQueue<QueuedMessage>(MAX_QUEUED_MESSAGES)
    private val queueConsumerActive = AtomicBoolean(false)

    /**
     * One overflow episode reports once, rather than every message that arrives while the queue stays full. Reset when the
     * consumer drains, so a later episode is still visible.
     */
    private val overflowReported = AtomicBoolean(false)

    /**
     * A message, the registration it arrived under, and — for domain restricted features only — the url of the document
     * that sent it. The url read is scheduled when the message arrives rather than when it is handled, so it is not
     * ordered behind everything else already queued: it decides whether a privileged feature may act on the message, and
     * a page can navigate while its message is still waiting.
     */
    private class QueuedMessage(
        val registration: Registration,
        val jsMessage: JsMessage,
        val url: Deferred<String?>?,
    )

    /**
     * Handler lookup by feature name and method, so routing a message doesn't have to build every registered handler.
     * The plugins are a fixed multibinding and their `featureName`/`methods` are constants, so the index is built once;
     * the matched handler is still rebuilt per message because `allowedDomains` isn't constant.
     */
    private val handlersByFeatureAndMethod: Map<String, Map<String, List<ContentScopeJsMessageHandlersPlugin>>> by lazy {
        val index = mutableMapOf<String, MutableMap<String, MutableList<ContentScopeJsMessageHandlersPlugin>>>()
        handlers.getPlugins().forEach { plugin ->
            val handler = plugin.getJsMessageHandler()
            handler.methods.forEach { method ->
                index
                    .getOrPut(handler.featureName) { mutableMapOf() }
                    .getOrPut(method) { mutableListOf() }
                    .add(plugin)
            }
        }
        index
    }

    /**
     * Features with at least one domain restricted handler, so we know whether a message needs its sending document's url
     * without scanning handlers on the JavaBridge thread. Whether `allowedDomains` is empty is fixed per handler even
     * though its contents are not, so this can be cached.
     */
    private val domainRestrictedFeatures: Set<String> by lazy {
        handlersByFeatureAndMethod
            .filterValues { methods ->
                methods.values.flatten().any { it.getJsMessageHandler().allowedDomains.isNotEmpty() }
            }.keys
    }

    // Sampled when the interface is attached rather than per message: the JavaBridge thread should not be reading
    // feature state on every inbound message, and a flag change takes effect on the next WebView anyway.
    @Volatile
    private var optimizedProcessing: Boolean = false

    override val context: String = "contentScopeScripts"
    override val callbackName: String = coreContentScopeScripts.callbackName
    override val secret: String = coreContentScopeScripts.secret
    override val allowedDomains: List<String> = emptyList()

    @JavascriptInterface
    override fun process(
        message: String,
        secret: String,
    ) {
        if (optimizedProcessing) {
            processOptimized(message, secret)
        } else {
            processLegacy(message, secret)
        }
    }

    private fun processOptimized(
        message: String,
        secret: String,
    ) {
        if (this.secret != secret) return
        val jsMessage = parseJsMessage(message) ?: return
        if (context != jsMessage.context) return
        val registration = this.registration ?: return

        val url =
            if (allowedDomains.isNotEmpty() || jsMessage.featureName in domainRestrictedFeatures) {
                appCoroutineScope.async(dispatcherProvider.main()) {
                    runCatching { registration.webView.url }.getOrNull()
                }
            } else {
                null
            }
        if (!inboundQueue.offer(QueuedMessage(registration, jsMessage, url))) {
            url?.cancel()
            logcat(WARN) { "Inbound queue full, dropping ${jsMessage.featureName}.${jsMessage.method}" }
            reportOverflow()
            return
        }
        ensureQueueConsumerRunning()
    }

    /**
     * A drop leaves any page waiting on a reply to this message waiting forever, which the synchronous JavaBridge call
     * could not do. Reported so the kill-switch decision doesn't depend on a logcat nobody is reading on a release build.
     */
    private fun reportOverflow() {
        if (!overflowReported.compareAndSet(false, true)) return
        pixel.fire(INBOUND_QUEUE_FULL, type = Daily())
    }

    private fun parseJsMessage(message: String): JsMessage? =
        runCatching {
            val json = JSONObject(message)
            val featureName = json.optString("featureName")
            val method = json.optString("method")
            when {
                featureName.isEmpty() || method.isEmpty() -> null
                else ->
                    JsMessage(
                        context = json.optString("context"),
                        featureName = featureName,
                        method = method,
                        params = json.optJSONObject("params") ?: JSONObject(),
                        id = if (json.isNull("id")) null else json.optString("id").takeUnless { it.isEmpty() },
                    )
            }
        }.onFailure { logcat(ERROR) { "Exception is ${it.asLog()}" } }
            .getOrNull()

    private fun ensureQueueConsumerRunning() {
        if (!queueConsumerActive.compareAndSet(false, true)) return
        appCoroutineScope.launch(dispatcherProvider.io()) {
            while (true) {
                try {
                    while (true) {
                        ensureActive()
                        val jsMessage = inboundQueue.poll() ?: break
                        try {
                            handle(jsMessage)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat(ERROR) { "Exception is ${e.asLog()}" }
                        }
                    }
                } finally {
                    queueConsumerActive.set(false)
                }
                if (inboundQueue.isEmpty()) {
                    overflowReported.set(false)
                    return@launch
                }
                if (!queueConsumerActive.compareAndSet(false, true)) return@launch
            }
        }
    }

    private suspend fun handle(queued: QueuedMessage) {
        if (queued.registration !== this.registration) {
            queued.url?.cancel()
            return
        }
        val jsMessage = queued.jsMessage
        val webView = queued.registration.webView
        val jsMessageCallback = queued.registration.jsMessageCallback
        val host = queued.url?.await()?.toUri()?.host

        if (allowedDomains.isNotEmpty() && !isUrlAllowed(allowedDomains, host)) return

        if (jsMessage.featureName == "webEvents" && jsMessage.method == "webEvent") {
            val nativeData = JSONObject()
            nativeData.put("webViewId", System.identityHashCode(webView).toString())
            jsMessage.params.put("nativeData", nativeData)
        }
        if (jsMessage.method == "addDebugFlag") {
            // If method is addDebugFlag, we want to handle it for all features
            jsMessageCallback.process(
                featureName = jsMessage.featureName,
                method = jsMessage.method,
                id = jsMessage.id,
                data = jsMessage.params,
            )
            return
        }

        val handler =
            handlersByFeatureAndMethod[jsMessage.featureName]
                ?.get(jsMessage.method)
                ?.firstNotNullOfOrNull { plugin ->
                    plugin.getJsMessageHandler().takeIf { isUrlAllowed(it.allowedDomains, host) }
                }

        if (handler == null) {
            jsMessage.id?.let { id -> sendMethodNotFoundError(jsMessage, id, webView) }
            return
        }
        handler.process(jsMessage, this, jsMessageCallback)
    }

    // Used when optimizeContentScopeMessaging is disabled; delete with the flag.
    private fun processLegacy(
        message: String,
        secret: String,
    ) {
        try {
            val registration = this.registration ?: return
            val webView = registration.webView
            val jsMessageCallback = registration.jsMessageCallback
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)
            val domain =
                runBlocking(dispatcherProvider.main()) {
                    webView.url?.toUri()?.host
                }
            jsMessage?.let {
                if (this.secret == secret && context == jsMessage.context && isUrlAllowed(allowedDomains, domain)) {
                    if (jsMessage.featureName == "webEvents" && jsMessage.method == "webEvent") {
                        val nativeData = JSONObject()
                        nativeData.put("webViewId", System.identityHashCode(webView).toString())
                        jsMessage.params.put("nativeData", nativeData)
                    }
                    if (jsMessage.method == "addDebugFlag") {
                        // If method is addDebugFlag, we want to handle it for all features
                        jsMessageCallback.process(
                            featureName = jsMessage.featureName,
                            method = jsMessage.method,
                            id = jsMessage.id,
                            data = jsMessage.params,
                        )
                    } else {
                        handlers
                            .getPlugins()
                            .map { it.getJsMessageHandler() }
                            .firstOrNull {
                                it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName &&
                                    isUrlAllowed(it.allowedDomains, domain)
                            }?.process(jsMessage, this, jsMessageCallback) ?: run {
                            jsMessage.id?.let { id -> sendMethodNotFoundError(jsMessage, id, webView) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Exception is ${e.asLog()}" }
        }
    }

    override fun register(
        webView: WebView,
        jsMessageCallback: JsMessageCallback?,
    ) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.registration = Registration(webView, jsMessageCallback)
        drainQueue()
        this.optimizedProcessing = contentScopeScriptsFeature.optimizeContentScopeMessaging().isEnabled()
        webView.addJavascriptInterface(this, coreContentScopeScripts.javascriptInterface)

        appCoroutineScope.launch(dispatcherProvider.io()) { domainRestrictedFeatures }
    }

    private fun drainQueue() {
        val discarded = mutableListOf<QueuedMessage>()
        inboundQueue.drainTo(discarded)
        discarded.forEach { it.url?.cancel() }
        overflowReported.set(false)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        val subscriptionEvent =
            SubscriptionEvent(
                context,
                subscriptionEventData.featureName,
                subscriptionEventData.subscriptionName,
                subscriptionEventData.params,
            )
        registration?.webView?.let {
            jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, it)
        }
    }

    override fun onResponse(response: JsCallbackData) {
        val jsResponse =
            JsRequestResponse.Success(
                context = context,
                featureName = response.featureName,
                method = response.method,
                id = response.id,
                result = response.params,
            )
        registration?.webView?.let {
            jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, it)
        }
    }

    private fun isUrlAllowed(
        allowedDomains: List<String>,
        url: String?,
    ): Boolean {
        if (allowedDomains.isEmpty()) return true
        val host = url ?: return false
        val eTld = host.toTldPlusOne()
        return allowedDomains.contains(host) || (eTld != null && allowedDomains.contains(eTld))
    }

    private fun sendMethodNotFoundError(
        jsMessage: JsMessage,
        id: String,
        webView: WebView,
    ) {
        jsMessageHelper.sendJsResponse(
            JsRequestResponse.Error(
                context = context,
                featureName = jsMessage.featureName,
                method = jsMessage.method,
                id = id,
                error = JsErrorDetails(
                    code = METHOD_NOT_FOUND_ERROR_CODE,
                    message = METHOD_NOT_FOUND_ERROR_MESSAGE,
                ),
            ),
            callbackName,
            secret,
            webView,
        )
    }

    companion object {
        private const val METHOD_NOT_FOUND_ERROR_CODE = -32601
        private const val METHOD_NOT_FOUND_ERROR_MESSAGE = "Method not found"
    }
}
