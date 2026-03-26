/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.webview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

interface ClipboardImageInjector {
    /**
     * Configures WebView for clipboard image support.
     * Uses modern WebMessageListener API if supported, otherwise falls back to legacy JavascriptInterface.
     */
    suspend fun configureWebViewForClipboard(webView: WebView)

    /**
     * Injects the legacy polyfill script into the WebView.
     * Should be called from WebViewClient.onPageStarted().
     */
    fun injectLegacyPolyfill(webView: WebView)
}

@ContributesBinding(AppScope::class)
class ClipboardImageInjectorImpl @Inject constructor(
    private val webViewClipboardImageFeature: WebViewClipboardImageFeature,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ClipboardImageInjector {

    private val clipboardManager by lazy { context.getSystemService(ClipboardManager::class.java) }
    private var legacyPolyfillScript: String? = null

    private var requiresLegacyPolyfillInjection: Boolean = true

    init {
        // Pre-load the legacy polyfill script at app startup to ensure it's ready before any WebView is created
        appCoroutineScope.launch(dispatcherProvider.io()) {
            legacyPolyfillScript = context.resources.openRawResource(R.raw.clipboard_polyfill_legacy)
                .bufferedReader()
                .use { it.readText() }
            logcat { "ClipboardImageInjector: Legacy polyfill script pre-loaded" }
        }
    }

    @SuppressLint("AddDocumentStartJavaScriptUsage", "RequiresFeature")
    override suspend fun configureWebViewForClipboard(webView: WebView) {
        if (!isFeatureEnabled()) {
            logcat { "ClipboardImageInjector: Feature flag not enabled" }
            requiresLegacyPolyfillInjection = false
            return
        }

        if (isModernApproachSupported()) {
            requiresLegacyPolyfillInjection = false
            configureModernApproach(webView)
        } else {
            requiresLegacyPolyfillInjection = true
            configureLegacyApproach(webView)
        }
    }

    private suspend fun isFeatureEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            webViewClipboardImageFeature.self().isEnabled()
        }
    }

    private suspend fun isModernApproachSupported(): Boolean {
        return withContext(dispatcherProvider.io()) {
            webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener) &&
                webViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)
        }
    }

    override fun injectLegacyPolyfill(webView: WebView) {
        if (requiresLegacyPolyfillInjection) {
            val script = legacyPolyfillScript
            if (script != null) {
                logcat { "ClipboardImageInjector: Injecting legacy polyfill script" }
                webView.evaluateJavascript("javascript:$script", null)
            } else {
                logcat { "ClipboardImageInjector: Legacy polyfill script not loaded yet" }
            }
        }
    }

    @SuppressLint("AddDocumentStartJavaScriptUsage", "RequiresFeature")
    private suspend fun configureModernApproach(webView: WebView) {
        logcat { "ClipboardImageInjector: Configuring modern approach (WebMessageListener)" }

        val script = getPolyfillScript()
        webViewCompatWrapper.addDocumentStartJavaScript(webView, script, setOf("*"))

        webViewCompatWrapper.addWebMessageListener(
            webView,
            "ddgClipboardObj",
            setOf("*"),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy,
                ) {
                    logcat { "ClipboardImageInjector: Received message from JS" }
                    handleClipboardMessage(webView, message.data, replyProxy)
                }
            },
        )
    }

    @SuppressLint("JavascriptInterface")
    private suspend fun configureLegacyApproach(webView: WebView) {
        logcat { "ClipboardImageInjector: Configuring legacy approach (JavascriptInterface)" }

        val jsInterface = ClipboardImageJavascriptInterface { dataUrl, mimeType ->
            logcat { "ClipboardImageInjector: Legacy interface called, mimeType: $mimeType, dataLength: ${dataUrl.length}" }
            handleLegacyClipboardRequest(dataUrl, mimeType)
        }

        withContext(dispatcherProvider.main()) {
            webView.addJavascriptInterface(
                jsInterface,
                ClipboardImageJavascriptInterface.JAVASCRIPT_INTERFACE_NAME,
            )
        }
    }

    private fun handleLegacyClipboardRequest(
        dataUrl: String,
        mimeType: String,
    ) {
        if (!mimeType.startsWith("image/") || dataUrl.isEmpty()) {
            logcat { "ClipboardImageInjector: Invalid legacy request - mimeType: $mimeType, hasData: ${dataUrl.isNotEmpty()}" }
            return
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                val imageData = extractBase64Data(dataUrl)
                if (imageData != null) {
                    val success = copyImageToClipboard(imageData, mimeType)
                    if (success) {
                        logcat { "ClipboardImageInjector: Legacy clipboard copy successful" }
                    } else {
                        logcat { "ClipboardImageInjector: Legacy clipboard copy failed" }
                    }
                } else {
                    logcat { "ClipboardImageInjector: Failed to decode image data in legacy request" }
                }
            }.onFailure { e ->
                logcat { "ClipboardImageInjector: Error in legacy clipboard handling: ${e.message}" }
            }
        }
    }

    private fun handleClipboardMessage(
        webView: WebView,
        data: String?,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (data == null) {
            logcat { "ClipboardImageInjector: Received null data" }
            return
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            var requestId = -1

            runCatching {
                val json = JSONObject(data)
                val type = json.optString("type")
                if (type != "clipboardWrite") {
                    logcat { "ClipboardImageInjector: Ignoring message type: $type" }
                    return@launch
                }

                requestId = json.optInt("requestId", -1)
                val mimeType = json.optString("mimeType")
                val base64Data = json.optString("data")

                logcat { "ClipboardImageInjector: Processing request $requestId, mimeType: $mimeType, dataLength: ${base64Data.length}" }

                if (mimeType.startsWith("image/") && base64Data.isNotEmpty()) {
                    val finalRequestId = requestId
                    runCatching {
                        val imageData = extractBase64Data(base64Data)
                        if (imageData != null) {
                            val success = copyImageToClipboard(imageData, mimeType)
                            if (success) {
                                sendResponse(webView, replyProxy, finalRequestId, true, null)
                            } else {
                                sendResponse(webView, replyProxy, finalRequestId, false, "Failed to copy image")
                            }
                        } else {
                            sendResponse(webView, replyProxy, finalRequestId, false, "Failed to decode image data")
                        }
                    }.onFailure { e ->
                        logcat { "ClipboardImageInjector: Error in background processing: ${e.message}" }
                        sendResponse(webView, replyProxy, finalRequestId, false, "Error: ${e.message}")
                    }
                } else {
                    logcat { "ClipboardImageInjector: Invalid image data - mimeType: $mimeType, hasData: ${base64Data.isNotEmpty()}" }
                    sendResponse(webView, replyProxy, requestId, false, "Invalid image data")
                }
            }.onFailure { e ->
                logcat { "ClipboardImageInjector: Error handling clipboard message: ${e.message}" }
                if (requestId >= 0) {
                    sendResponse(webView, replyProxy, requestId, false, "Error: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private suspend fun sendResponse(
        webView: WebView,
        replyProxy: JavaScriptReplyProxy,
        requestId: Int,
        success: Boolean,
        error: String?,
    ) {
        runCatching {
            val response = JSONObject().apply {
                put("type", "clipboardWriteResponse")
                put("requestId", requestId)
                put("success", success)
                if (error != null) {
                    put("error", error)
                }
            }
            logcat { "ClipboardImageInjector: Sending response for request $requestId, success: $success" }
            withContext(dispatcherProvider.main()) {
                webViewCompatWrapper.postMessage(webView, replyProxy, response.toString())
            }
        }.onFailure { e ->
            logcat { "ClipboardImageInjector: Error sending response: ${e.message}" }
        }
    }

    private fun extractBase64Data(dataUrl: String): ByteArray? {
        return runCatching {
            val base64String = if (dataUrl.contains(",")) {
                dataUrl.substringAfter(",")
            } else {
                dataUrl
            }
            Base64.decode(base64String, Base64.DEFAULT)
        }.onFailure { e ->
            logcat { "ClipboardImageInjector: Error decoding base64: ${e.message}" }
        }.getOrNull()
    }

    @SuppressLint("DenyListedApi", "NewApi")
    private suspend fun copyImageToClipboard(imageData: ByteArray, mimeType: String): Boolean {
        return runCatching {
            logcat { "ClipboardImageInjector: Decoding bitmap, size: ${imageData.size}" }
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap == null) {
                logcat { "ClipboardImageInjector: Failed to decode bitmap" }
                return false
            }

            logcat { "ClipboardImageInjector: Bitmap decoded: ${bitmap.width}x${bitmap.height}" }
            val uri = saveBitmapToCache(bitmap, mimeType)
            if (uri != null) {
                val clipboardSuccess = withContext(dispatcherProvider.main()) {
                    runCatching {
                        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.P) {
                            clipboardManager.clearPrimaryClip()
                        }

                        val clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                        clipboardManager.setPrimaryClip(clipData)
                        logcat { "ClipboardImageInjector: Clipboard updated with uri: $uri" }
                        true
                    }.onFailure { e ->
                        logcat { "ClipboardImageInjector: Error setting clipboard: ${e.message}" }
                    }.getOrDefault(false)
                }
                if (clipboardSuccess) {
                    logcat { "ClipboardImageInjector: Image copied to clipboard successfully, uri: $uri" }
                }
                clipboardSuccess
            } else {
                logcat { "ClipboardImageInjector: Failed to save bitmap to cache" }
                false
            }
        }.onFailure { e ->
            logcat { "ClipboardImageInjector: Error copying image to clipboard: ${e.message}" }
        }.getOrDefault(false)
    }

    @SuppressLint("DenyListedApi")
    private fun saveBitmapToCache(bitmap: Bitmap, mimeType: String): Uri? {
        return runCatching {
            val format = when {
                mimeType.contains("png") -> Bitmap.CompressFormat.PNG
                mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }

            val (extension, actualMimeType) = when (format) {
                Bitmap.CompressFormat.PNG -> "png" to "image/png"
                Bitmap.CompressFormat.WEBP -> "webp" to "image/webp"
                else -> "jpg" to "image/jpeg"
            }

            val filename = "$CLIPBOARD_IMAGE_FILENAME.$extension"

            if (appBuildConfig.sdkInt >= Build.VERSION_CODES.Q) {
                // Delete any existing clipboard image with this name
                deletePreviousMediaStoreImage(filename)

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, actualMimeType)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                )

                if (uri == null) {
                    logcat { "ClipboardImageInjector: Failed to insert into MediaStore" }
                    return@runCatching null
                }

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    logcat { "ClipboardImageInjector: Failed to open output stream, deleting invalid entry" }
                    context.contentResolver.delete(uri, null, null)
                    return@runCatching null
                }

                val compressSuccess = outputStream.use {
                    bitmap.compress(format, 100, it)
                }

                if (!compressSuccess) {
                    logcat { "ClipboardImageInjector: Bitmap compression failed, deleting invalid entry" }
                    context.contentResolver.delete(uri, null, null)
                    return@runCatching null
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                uri
            } else {
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val file = File(cacheDir, filename)
                // File will be overwritten if it exists
                val compressSuccess = FileOutputStream(file).use { outputStream ->
                    bitmap.compress(format, 100, outputStream)
                }

                if (!compressSuccess) {
                    logcat { "ClipboardImageInjector: Bitmap compression failed, deleting file" }
                    file.delete()
                    return@runCatching null
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file,
                )
            }
        }.onFailure { e ->
            logcat { "ClipboardImageInjector: Error saving bitmap: ${e.message}" }
        }.getOrNull()
    }

    @SuppressLint("DenyListedApi")
    private fun deletePreviousMediaStoreImage(filename: String) {
        runCatching {
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(filename)
            val deleted = context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                selection,
                selectionArgs,
            )
            if (deleted > 0) {
                logcat { "ClipboardImageInjector: Deleted previous clipboard image: $deleted rows" }
            }
        }.onFailure { e ->
            logcat { "ClipboardImageInjector: Failed to delete previous clipboard image: ${e.message}" }
        }
    }

    private suspend fun getPolyfillScript(): String {
        return withContext(dispatcherProvider.io()) {
            context.resources.openRawResource(R.raw.clipboard_polyfill)
                .bufferedReader()
                .use { it.readText() }
        }
    }

    companion object {
        private const val CLIPBOARD_IMAGE_FILENAME = "ddg_clipboard_image"
    }
}
