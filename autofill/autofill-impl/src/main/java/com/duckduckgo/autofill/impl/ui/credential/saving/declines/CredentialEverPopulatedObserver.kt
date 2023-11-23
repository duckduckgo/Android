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

package com.duckduckgo.autofill.impl.ui.credential.saving.declines

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class CredentialEverPopulatedObserver @Inject constructor(
    private val declineCounter: AutofillDisablingDeclineCounter,
    private val autofillStore: AutofillStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        appCoroutineScope.launch(dispatchers.io()) {
            observeForCredentials()
        }
    }

    private suspend fun observeForCredentials() {
        autofillStore.getCredentialCount()
            .takeWhile { declineCounter.isActive() }
            .collect { numberCredentials ->
                Timber.d("CredentialEverPopulatedObserver received number of stored credentials: $numberCredentials")

                // when any credentials added, we can permanently disable the decline counter and stop observing the credential count
                if (numberCredentials > 0) {
                    declineCounter.disableDeclineCounter()
                    coroutineContext.job.cancel()
                }
            }
    }
}
