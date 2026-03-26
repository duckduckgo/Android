/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.SavedDatasetsInfoCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@InjectWith(scope = ServiceScope::class)
class RealAutofillService : AutofillService() {

    @Inject
    @AppCoroutineScope
    lateinit var coroutineScope: CoroutineScope

    @Inject lateinit var autofillServiceFeature: AutofillServiceFeature

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var autofillParser: AutofillParser

    @Inject lateinit var autofillProviderSuggestions: AutofillProviderSuggestions

    @Inject lateinit var autofillServiceExceptions: AutofillServiceExceptions

    @Inject lateinit var pixel: Pixel

    private val autofillJob = ConflatedJob()

    override fun onCreate() {
        super.onCreate()
        logcat(VERBOSE) { "DDGAutofillService created" }
        AndroidInjection.inject(this)
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        logcat(VERBOSE) { "DDGAutofillService onFillRequest: $request" }
        cancellationSignal.setOnCancelListener { autofillJob.cancel() }

        autofillJob += coroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                if (isAutofillServiceEnabled().not()) {
                    callback.onSuccess(null)
                    return@launch
                }
                val structure = request.fillContexts.lastOrNull()?.structure
                if (structure == null) {
                    callback.onSuccess(null)
                    return@launch
                }
                val parsedRootNodes = autofillParser.parseStructure(structure)
                val nodeToAutofill = parsedRootNodes.findBestFillableNode()
                if (nodeToAutofill == null || shouldSkipAutofillSuggestions(nodeToAutofill)) {
                    logcat(VERBOSE) { "DDGAutofillService onFillRequest: no autofill suggestions" }
                    callback.onSuccess(null)
                    return@launch
                }

                // prepare response
                val response = autofillProviderSuggestions.buildSuggestionsResponse(
                    context = this@RealAutofillService,
                    nodeToAutofill = nodeToAutofill,
                    request = request,
                )

                callback.onSuccess(response)
            }.onFailure {
                if (it !is kotlinx.coroutines.CancellationException) {
                    pixel.fire(AutofillPixelNames.AUTOFILL_SERVICE_CRASH, mapOf("message" to it.extractExceptionCause()))
                }
                callback.onSuccess(null)
            }
        }
    }

    private fun Throwable?.extractExceptionCause(): String {
        if (this == null) {
            return "Exception missing"
        }
        return "${this.javaClass.name} - ${this.stackTrace.firstOrNull()}"
    }

    private fun shouldSkipAutofillSuggestions(nodeToAutofill: AutofillRootNode): Boolean {
        if (nodeToAutofill.packageId.isNullOrBlank() && nodeToAutofill.website.isNullOrBlank()) {
            return true
        }

        if (nodeToAutofill.packageId.equals("android", ignoreCase = true)) return true

        if (autofillServiceFeature.canAutofillInsideDDG().isEnabled().not() && nodeToAutofill.packageId in DDG_PACKAGE_IDS) {
            return true
        }

        nodeToAutofill.packageId.takeUnless { it.isNullOrBlank() }?.let {
            if (autofillServiceExceptions.isAnException(it)) {
                return true
            }
        }

        nodeToAutofill.website.takeUnless { it.isNullOrBlank() }?.let {
            if (autofillServiceExceptions.isAnException(it)) {
                return true
            }
        }

        if (nodeToAutofill.packageId in BROWSERS_PACKAGE_IDS && nodeToAutofill.website.isNullOrBlank()) {
            return true // if a browser we require a website
        }

        return false
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        logcat(VERBOSE) { "DDGAutofillService onSaveRequest" }
    }

    override fun onConnected() {
        super.onConnected()
        logcat(VERBOSE) { "DDGAutofillService onConnected" }
    }

    override fun onSavedDatasetsInfoRequest(callback: SavedDatasetsInfoCallback) {
        super.onSavedDatasetsInfoRequest(callback)
        logcat(VERBOSE) { "DDGAutofillService onSavedDatasetsInfoRequest" }
    }

    override fun onDisconnected() {
        super.onDisconnected()
        logcat(VERBOSE) { "DDGAutofillService onDisconnected" }
    }

    private fun isAutofillServiceEnabled(): Boolean {
        return autofillServiceFeature.self().isEnabled() && autofillServiceFeature.canProcessSystemFillRequests().isEnabled()
    }

    companion object {
        private val DDG_PACKAGE_IDS = setOf(
            "com.duckduckgo.mobile.android",
            "com.duckduckgo.mobile.android.debug",
        )
        private val BROWSERS_PACKAGE_IDS = DDG_PACKAGE_IDS + setOf(
            "alook.browser",
            "alook.browser.google",
            "alook.browser.play",
            "app.vanadium.browser",
            "app.vanadium.webview",
            "com.aloha.browser",
            "com.alohamobile.browser",
            "com.amazon.cloud9",
            "com.android.browser",
            "com.android.chrome",
            "com.android.htmlviewer",
            "com.avast.android.secure.browser",
            "com.avg.android.secure.browser",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_default",
            "com.brave.browser_dev",
            "com.brave.browser_nightly",
            "com.chrome.beta",
            "com.chrome.canary",
            "com.chrome.dev",
            "com.cookiegames.smartcookie",
            "com.cookiejarapps.android.smartcookieweb",
            "com.ecosia.android",
            "com.google.android.apps.chrome",
            "com.google.android.apps.chrome_dev",
            "com.google.android.captiveportallogin",
            "com.iode.firefox",
            "com.kiwibrowser.browser",
            "com.kiwibrowser.browser.dev",
            "com.lemurbrowser.exts",
            "com.microsoft.emmx",
            "com.microsoft.emmx.beta",
            "com.microsoft.emmx.canary",
            "com.microsoft.emmx.dev",
            "com.mmbox.browser",
            "com.mmbox.xbrowser",
            "com.mycompany.app.soulbrowser",
            "com.naver.whale",
            "com.opera.browser",
            "com.opera.browser.beta",
            "com.opera.gx",
            "com.opera.mini.native",
            "com.opera.mini.native.beta",
            "com.opera.touch",
            "com.qflair.browserq",
            "com.qwant.liberty",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.startpage.app",
            "com.stoutner.privacybrowser.free",
            "com.stoutner.privacybrowser.standard",
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            "com.vivaldi.browser.sopranos",
            "com.yandex.browser",
            "com.z28j.feel",
            "idm.internet.download.manager",
            "idm.internet.download.manager.adm.lite",
            "idm.internet.download.manager.plus",
            "io.github.forkmaintainers.iceraven",
            "mark.via",
            "mark.via.gp",
            "net.dezor.browser",
            "net.quetta.browser",
            "net.slions.fulguris.full.download",
            "net.slions.fulguris.full.download.debug",
            "net.slions.fulguris.full.playstore",
            "net.slions.fulguris.full.playstore.debug",
            "net.waterfox.android.release",
            "org.adblockplus.browser",
            "org.adblockplus.browser.beta",
            "org.bromite.bromite",
            "org.bromite.chromium",
            "org.chromium.chrome",
            "org.chromium.webview_shell",
            "org.codeaurora.swe.browser",
            "org.gnu.icecat",
            "org.mozilla.fenix",
            "org.mozilla.fenix.nightly",
            "org.mozilla.fennec_aurora",
            "org.mozilla.fennec_fdroid",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "org.mozilla.reference.browser",
            "org.mozilla.rocket",
            "org.torproject.torbrowser",
            "org.torproject.torbrowser_alpha",
            "org.ungoogled.chromium.extensions.stable",
            "org.ungoogled.chromium.stable",
            "us.spotco.fennec_dos",
            "us.spotco.mulch",
        )
    }
}
