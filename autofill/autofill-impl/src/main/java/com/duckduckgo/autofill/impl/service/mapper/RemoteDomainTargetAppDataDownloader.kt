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

package com.duckduckgo.autofill.impl.service.mapper

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RemoteDomainTargetAppDataDownloader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val remoteDomainTargetAppService: RemoteDomainTargetAppService,
    private val autofillPrefsStore: AutofillPrefsStore,
    private val domainTargetAppDao: DomainTargetAppDao,
    private val currentTimeProvider: CurrentTimeProvider,
    private val autofillServiceFeature: AutofillServiceFeature,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (autofillServiceFeature.canUpdateAppToDomainDataset().isEnabled().not()) return@launch

            logcat { "Autofill-mapping: Attempting to download" }
            download()
            removeExpiredCachedData()
        }
    }

    private suspend fun download() {
        runCatching {
            remoteDomainTargetAppService.fetchDataset().run {
                logcat { "Autofill-mapping: Downloaded targets dataset version: ${this.version}" }
                if (autofillPrefsStore.domainTargetDatasetVersion != this.version) {
                    persistData(this)
                    autofillPrefsStore.domainTargetDatasetVersion = this.version
                }
            }
        }.onFailure {
            logcat(ERROR) { "Autofill-mapping: Dataset download failed: ${it.asLog()}" }
        }
    }

    private fun persistData(dataset: RemoteDomainTargetDataSet) {
        logcat { "Autofill-mapping: Persisting targets dataset" }
        val toPersist = mutableListOf<DomainTargetAppEntity>()
        dataset.targets.forEach { target ->
            target.apps.forEach { app ->
                app.sha256_cert_fingerprints.forEach {
                    toPersist.add(
                        DomainTargetAppEntity(
                            domain = target.url,
                            targetApp = TargetApp(
                                packageName = app.package_name,
                                sha256CertFingerprints = it,
                            ),
                            dataExpiryInMillis = 0L,
                        ),
                    )
                }
            }
        }
        logcat { "Autofill-mapping: Attempting to persist ${toPersist.size} entries" }
        domainTargetAppDao.updateRemote(toPersist)
        logcat { "Autofill-mapping: Persist complete" }
    }

    private fun removeExpiredCachedData() {
        logcat { "Autofill-mapping: Removing expired cached data" }
        domainTargetAppDao.deleteAllExpired(currentTimeProvider.currentTimeMillis())
    }
}
