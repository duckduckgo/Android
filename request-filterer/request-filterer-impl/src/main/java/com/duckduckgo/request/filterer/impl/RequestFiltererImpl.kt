/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.request.filterer.impl

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.request.filterer.api.RequestFiltererFeatureName
import com.duckduckgo.request.filterer.store.RequestFiltererRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RequestFiltererImpl @Inject constructor(
    private val repository: RequestFiltererRepository,
    private val toggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary,
    val dispatcherProvider: DispatcherProvider,
) : RequestFilterer {

    private val scope = CoroutineScope(dispatcherProvider.io())
    private val windowInMs = repository.settings.windowInMs.toLong()

    var job: Job? = null
    private var previousPage: String? = null
    private var currentPage: String? = null
    private var hasTimeElapsed: Boolean = true

    override fun shouldFilterOutRequest(request: WebResourceRequest, documentUrl: String?): Boolean {
        if (documentUrl.isNullOrEmpty()) return false
        if (!isFeatureEnabled()) return false
        if (hasTimeElapsed) return false
        if (isAnException(documentUrl)) return false

        val origin = request.requestHeaders[ORIGIN]
        val referer = request.requestHeaders[REFERER]

        runCatching {
            val currentTopDomain = documentUrl.toHttpUrl().topPrivateDomain()
            val previousTopDomain = previousPage?.toHttpUrl()?.topPrivateDomain()

            if (currentTopDomain != previousTopDomain) {
                referer?.let {
                    return compareUrl(it)
                }
                origin?.let {
                    return compareUrl(it)
                }
            }
        }
        return false
    }

    override fun registerOnPageCreated(url: String) {
        previousPage = currentPage
        currentPage = url
        hasTimeElapsed = false
        if (job?.isActive == true) job?.cancel()
        job = scope.launch(dispatcherProvider.io()) {
            delay(windowInMs)
            hasTimeElapsed = true
        }
    }

    private fun compareUrl(url: String): Boolean {
        return try {
            val uri = url.toUri()
            val previousUri = previousPage?.toUri()
            if (uri.path.isNullOrEmpty()) {
                return uri.host == previousUri?.host
            }
            return url == previousPage
        } catch (e: Exception) {
            logcat { e.localizedMessage }
            false
        }
    }

    private fun isAnException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || matches(url)
    }

    private fun matches(url: String): Boolean {
        return repository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }
    private fun isFeatureEnabled(): Boolean = toggle.isFeatureEnabled(RequestFiltererFeatureName.RequestFilterer.value)

    companion object {
        const val ORIGIN = "Origin"
        const val REFERER = "Referer"
    }
}
