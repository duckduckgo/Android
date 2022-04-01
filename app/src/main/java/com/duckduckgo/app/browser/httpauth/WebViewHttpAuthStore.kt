/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.httpauth

import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.browser.WebViewDatabaseProvider
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.DatabaseCleaner
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import dagger.SingleInstanceIn

data class WebViewHttpAuthCredentials(
    val username: String,
    val password: String
)

// Methods are marked to run in the UiThread because it is the thread of webview
// if necessary the method impls will change thread to access the http auth dao
interface WebViewHttpAuthStore {
    @UiThread
    fun setHttpAuthUsernamePassword(
        webView: WebView,
        host: String,
        realm: String,
        username: String,
        password: String
    )

    @UiThread
    fun getHttpAuthUsernamePassword(
        webView: WebView,
        host: String,
        realm: String
    ): WebViewHttpAuthCredentials?

    @UiThread
    fun clearHttpAuthUsernamePassword(webView: WebView)

    @WorkerThread
    suspend fun cleanHttpAuthDatabase()
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class
)
@SingleInstanceIn(AppScope::class)
class RealWebViewHttpAuthStore @Inject constructor(
    private val webViewDatabaseProvider: WebViewDatabaseProvider,
    private val databaseCleaner: DatabaseCleaner,
    @Named("authDbLocator") private val authDatabaseLocator: DatabaseLocator,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : WebViewHttpAuthStore, LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreated() {
        // API 28 seems to use WAL for the http_auth db and changing the journal mode does not seem
        // to work properly
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.P) return

        appCoroutineScope.launch(dispatcherProvider.io()) {
            databaseCleaner.changeJournalModeToDelete(authDatabaseLocator.getDatabasePath())
        }
    }

    override fun setHttpAuthUsernamePassword(
        webView: WebView,
        host: String,
        realm: String,
        username: String,
        password: String
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webViewDatabaseProvider.get().setHttpAuthUsernamePassword(host, realm, username, password)
        } else {
            webView.setHttpAuthUsernamePassword(host, realm, username, password)
        }
    }

    override fun getHttpAuthUsernamePassword(
        webView: WebView,
        host: String,
        realm: String
    ): WebViewHttpAuthCredentials? {
        val credentials = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webViewDatabaseProvider.get().getHttpAuthUsernamePassword(host, realm)
        } else {
            @Suppress("DEPRECATION")
            webView.getHttpAuthUsernamePassword(host, realm)
        } ?: return null

        return WebViewHttpAuthCredentials(username = credentials[0], password = credentials[1])
    }

    override fun clearHttpAuthUsernamePassword(webView: WebView) {
        webViewDatabaseProvider.get().clearHttpAuthUsernamePassword()
    }

    override suspend fun cleanHttpAuthDatabase() {
        databaseCleaner.cleanDatabase(authDatabaseLocator.getDatabasePath())
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class WebViewHttpAuthStoreModule {
    @Binds
    @SingleInstanceIn(AppScope::class)
    abstract fun bindWebViewHttpAuthStore(
        realWebViewHttpAuthStore: RealWebViewHttpAuthStore
    ): WebViewHttpAuthStore
}
