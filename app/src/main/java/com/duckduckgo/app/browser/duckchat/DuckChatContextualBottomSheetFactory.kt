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

package com.duckduckgo.app.browser.duckchat

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.DuckChatContextualBottomSheet
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewClient
import com.duckduckgo.duckchat.impl.ui.SubscriptionsHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Named

interface DuckChatContextualBottomSheetFactory {
    fun create(): DuckChatContextualBottomSheet
}

@ContributesBinding(scope = FragmentScope::class)
@SingleInstanceIn(scope = FragmentScope::class)
class DuckChatContextualBottomSheetFactoryImpl @Inject constructor() : DuckChatContextualBottomSheetFactory {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var duckChatWebViewClient: DuckChatWebViewClient

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    lateinit var duckChatJSHelper: DuckChatJSHelper

    @Inject
    lateinit var subscriptionsHandler: SubscriptionsHandler

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    @Inject
    lateinit var aiChatDownloadFeature: AIChatDownloadFeature

    @Inject
    lateinit var duckChat: DuckChat

    override fun create(): DuckChatContextualBottomSheet {
        return DuckChatContextualBottomSheet(
            viewModelFactory = viewModelFactory,
            webViewClient = duckChatWebViewClient,
            contentScopeScripts = contentScopeScripts,
            duckChatJSHelper = duckChatJSHelper,
            subscriptionsHandler = subscriptionsHandler,
            appCoroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
            browserNav = browserNav,
            appBuildConfig = appBuildConfig,
            fileDownloader = fileDownloader,
            downloadCallback = downloadCallback,
            downloadsFileActions = downloadsFileActions,
            aiChatDownloadFeature = aiChatDownloadFeature,
            duckChat = duckChat,
        )
    }
}
