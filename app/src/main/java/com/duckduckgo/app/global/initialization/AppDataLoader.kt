/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.initialization

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AppDataLoader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val termsOfServiceStore: TermsOfServiceStore,
    private val privacyPractices: PrivacyPractices
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun loadData() {
        appCoroutineScope.launch {
            Timber.i("Started to load app data")
            termsOfServiceStore.loadData()
            privacyPractices.loadData()
            Timber.i("Finished loading app data")
        }
    }
}
