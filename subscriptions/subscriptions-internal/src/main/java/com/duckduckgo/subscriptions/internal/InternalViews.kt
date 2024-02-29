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

package com.duckduckgo.subscriptions.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.subscriptions.impl.AccessToken
import com.duckduckgo.subscriptions.impl.SubscriptionsData
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesMultibinding(AppScope::class)
class InternalDeleteView @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    val context: Context,
) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Delete Subscription Account"
    }

    override fun internalFeatureSubtitle(): String {
        return "Deletes your subscription account"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val message = if (subscriptionsManager.deleteAccount()) {
                subscriptionsManager.signOut()
                "Account deleted"
            } else {
                "We could not delete your account"
            }
            withContext(dispatcherProvider.main()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@ContributesMultibinding(AppScope::class)
class CopyDataView @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val subscriptionsDataStore: SubscriptionsDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    val context: Context,
) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Copy subscriptions data"
    }

    override fun internalFeatureSubtitle(): String {
        return "Copies your data to the clipboard"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val auth = subscriptionsDataStore.authToken
            val authToken = if (auth.isNullOrBlank()) {
                "No auth token found"
            } else {
                auth
            }

            val access = subscriptionsManager.getAccessToken()
            val accessToken = if (access is AccessToken.Success) {
                access.accessToken
            } else {
                "No access token found"
            }

            val external = subscriptionsManager.getSubscriptionData()
            val externalId = if (external is SubscriptionsData.Success) {
                external.externalId
            } else {
                "No external id found"
            }
            val text = "Auth token is $authToken || Access token is $accessToken || External id is $externalId"

            clipboardManager.setPrimaryClip(ClipData.newPlainText("", text))

            withContext(dispatcherProvider.main()) {
                Toast.makeText(context, "Data copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
